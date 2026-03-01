package com.tianji.learning.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final InteractionReplyMapper replyMapper;
    private final UserClient userClient;
    private final IInteractionQuestionService questionService;
    private final SearchClient searchClient;
    private final CategoryClient categoryClient;
    private final CategoryCache categoryCache;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final IInteractionReplyService replyService;
    @ApiOperation("新增提问")
    @PostMapping
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionDTO){
        questionService.saveQuestion(questionDTO);
    }

    //分页查询评论
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        // 1.参数校验，课程id和小节id不能都为空
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if (courseId == null && sectionId == null) {
            throw new BadRequestException(" 课程id 和 小节id 不能都为空");
        }
        // 2.分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.根据id查询提问者和最近一次回答的信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        // 3.1.得到问题当中的提问者id和最近一次回答的id
        for (InteractionQuestion q : records) {
            if(!q.getAnonymity()) { // 只查询非匿名的问题
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        // 3.2.根据id查询最近一次回答
        answerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if(CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyMapper.selectBatchIds(answerIds);
            for (InteractionReply reply : replies) {
                replyMap.put(reply.getId(), reply);
                if(!reply.getAnonymity()){ // 匿名用户不做查询
                    userIds.add(reply.getUserId());
                }
            }
        }

        // 3.3.根据id查询用户信息（提问者）
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 4.封装VO
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            // 4.1.将PO转为VO
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            vo.setUserId(null);
            voList.add(vo);
            // 4.2.封装提问者信息
            if(!r.getAnonymity()){
                UserDTO userDTO = userMap.get(r.getUserId());
                if (userDTO != null) {
                    vo.setUserId(userDTO.getId());
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }

            // 4.3.封装最近一次回答的信息
            InteractionReply reply = replyMap.get(r.getLatestAnswerId());
            if (reply != null) {
                vo.setLatestReplyContent(reply.getContent());
                if(!reply.getAnonymity()){// 匿名用户直接忽略
                    UserDTO user = userMap.get(reply.getUserId());
                    vo.setLatestReplyUser(user.getName());
                }

            }
        }

        return PageDTO.of(page, voList);
    }


    //根据id查询评论详情
    @Override
    public  QuestionVO queryQuestionById(Long id){
        InteractionQuestion question = getById(id);
        if (question==null||question.getHidden()){
            return null;
        }
        //查询提问者信息
        // 1. 基础属性拷贝
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        // 2. 根据匿名状态处理用户信息
        if (question.getAnonymity()) {
            // 如果是匿名，务必抹除可能被拷贝进去的 userId
            vo.setUserId(null);
            vo.setUserName("匿名用户");
            vo.setUserIcon(null);
        } else {
            // 非匿名，查询并填充用户信息
            UserDTO user = userClient.queryUserById(question.getUserId());
            if (user != null) {
                vo.setUserId(user.getId()); // 显式设置，防止 BeanUtils 拷贝失败
                vo.setUserName(user.getName());
                vo.setUserIcon(user.getIcon());
            }
        }
        return vo;
    }


    //管理端分页查询评论
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        // 1.处理课程名称，得到课程id
        List<Long> courseIds = null;
        if (StringUtils.isNotBlank(query.getCourseName())) {
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if (CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L, 0L);
            }
        }
        // 2.分页查询
        Integer status = query.getStatus();
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(begin != null, InteractionQuestion::getCreateTime, begin)
                .lt(end != null, InteractionQuestion::getCreateTime, end)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3.准备VO需要的数据：用户数据、课程数据、章节数据
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        // 3.1.获取各种数据的id集合
        for (InteractionQuestion q : records) {
            userIds.add(q.getUserId());
            cIds.add(q.getCourseId());
            cataIds.add(q.getChapterId());
            cataIds.add(q.getSectionId());
        }
        // 3.2.根据id查询用户
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(users.size());
        if (CollUtils.isNotEmpty(users)) {
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 3.3.根据id查询课程
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> cInfoMap = new HashMap<>(cInfos.size());
        if (CollUtils.isNotEmpty(cInfos)) {
            cInfoMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }

        // 3.4.根据id查询章节
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> cataMap = new HashMap<>(catas.size());
        if (CollUtils.isNotEmpty(catas)) {
            cataMap = catas.stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }

        // 4.封装VO
        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            // 4.1.将PO转VO，属性拷贝
            QuestionAdminVO vo = BeanUtils.copyBean(q, QuestionAdminVO.class);
            voList.add(vo);
            // 4.2.用户信息
            UserDTO user = userMap.get(q.getUserId());
            if (user != null) {
                vo.setUserName(user.getName());
            }
            // 4.3.课程信息以及分类信息
            CourseSimpleInfoDTO cInfo = cInfoMap.get(q.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            }
            // 4.4.章节信息
            vo.setChapterName(cataMap.getOrDefault(q.getChapterId(), ""));
            vo.setSectionName(cataMap.getOrDefault(q.getSectionId(), ""));
        }
        return PageDTO.of(page, voList);
    }
    @Transactional
    @Override
    public void deleteMyQuestion(Long id) {
        //获取用户登录
        Long userId = UserContext.getUser();
        //查找问题
        InteractionQuestion question = this.getById(id);
        //判断问题是否存在
        if (question == null) {
            throw new BizIllegalException("问题不存在");
        }
        //判断问问题的用户id和 当前登录id 是否一致
        if (question.getUserId() != userId) {
            throw new BizIllegalException("该问题提问者非当前用户,无法删除");
        }
        //是的话 则删除问题
        this.removeById(id);
        //删除问题下的所有回复
        LambdaQueryWrapper<InteractionReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InteractionReply::getQuestionId, question.getId());
        replyService.remove(wrapper);


    }
    //修改我的问题
    @Override
    public void updateMyQuestion(Long id,QuestionFormDTO dto){
        Long userId = UserContext.getUser();
        InteractionQuestion question = this.getById(id);
        if(dto.getAnonymity()==null || StrUtil.isBlank(dto.getTitle())
                || StrUtil.isBlank(dto.getDescription())){
            throw new BizIllegalException("参数异常");
        }
        if(question==null){
            throw new BizIllegalException("参数异常");
        }
        if(!userId.equals(question.getUserId())){
            throw new BadRequestException("无法修改他人问题");
        }
        question.setDescription(dto.getDescription());
        question.setTitle(dto.getTitle());
        question.setStatus(QuestionStatus.UN_CHECK);
        question.setAnonymity(dto.getAnonymity());
        this.updateById(question);
    }

    //管理端隐藏问题
    @Override
    public void hiddenQuestionAdmin(Long id, boolean hidden){
        InteractionQuestion question = questionService.getById(id);
        if(question==null){
            throw new BadRequestException("获取问题信息失败");
        }
        question.setHidden(hidden);
        this.updateById(question);
    }

    //管理端根据id查询问题详情
    @Override
    public QuestionAdminVO queryQuestionAdminById(Long id){
        if(id==null){
            throw new BadRequestException("非法参数");
        }
        InteractionQuestion question = this.getById(id);
        if(question==null){
            throw new BadRequestException("该问题不存在");
        }
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        //用户头像 用户名称 分类
        UserDTO userDTO = userClient.queryUserById(question.getUserId());
        if(userDTO!=null){
            vo.setUserName(userDTO.getName());
            vo.setUserIcon(userDTO.getIcon());
            CourseFullInfoDTO courseDTO = courseClient.getCourseInfoById(question.getCourseId(),true,true);
            if(courseDTO!=null){
                vo.setCourseName(courseDTO.getName());
            }
            vo.setCategoryName(categoryCache.getCategoryNames(courseDTO.getCategoryIds()));
        }
        //获取 章 节 信息
        //6、调用课程服务，获取章和节信息
        Set<Long> chapterAndSectionIds = new HashSet<>(); //章和节id集合
        chapterAndSectionIds.add(question.getChapterId());
        chapterAndSectionIds.add(question.getSectionId());
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            throw new BizIllegalException("章和节不存在！");
        }
        Map<Long, String> cataSimpleInfoDTOMap = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c.getName()));
        vo.setChapterName(cataSimpleInfoDTOMap.get(question.getChapterId())); //章名称
        vo.setSectionName(cataSimpleInfoDTOMap.get(question.getSectionId())); //节名称

        //7、查看完成，将问题status标记为已查看
        question.setStatus(QuestionStatus.CHECKED);
        this.updateById(question);

        //5、返回vo
        return vo;

    }
}