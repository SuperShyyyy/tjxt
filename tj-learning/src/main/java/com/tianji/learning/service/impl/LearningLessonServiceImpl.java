package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mysql.cj.protocol.WriterWatcher;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.Range;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-02-20
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordServiceImpl recordService;
    private final LearningRecordMapper recordMapper;

    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds){
        //1.查询课程有效期

        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cInfoList)){
            log.error("课程信息不存在,无法添加到课表");
            return;
        }
        List<LearningLesson> list = cInfoList.stream().map(cInfo->{
            LearningLesson lesson = new LearningLesson();
            Integer expiredTime =  cInfo.getValidDuration();
            if(expiredTime!=null) {
                lesson.setExpireTime(LocalDateTime.now().plusMonths(expiredTime));
                lesson.setCreateTime(LocalDateTime.now());
            }
            lesson.setCourseId(cInfo.getId());
            lesson.setUserId(userId);
            return lesson;
        }).collect(Collectors.toList());

        //3.批量新增
        saveBatch(list);
    }
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query){
        Long userId = UserContext.getUser();

        //2.分页查询
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getCourseId,userId)
                .page(query.toMpPage("latest_learn_time",false));
       List<LearningLesson> records = page.getRecords();
       if(CollUtils.isEmpty(records)){
           return PageDTO.empty(page);
       }
       //3获取课程信息
        Map<Long,CourseSimpleInfoDTO> map = queryCourseSimpleInfoList(records);
        //4拷贝到vo
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        for(LearningLesson r : records){
            LearningLessonVO vo=  BeanUtils.copyBean(r, LearningLessonVO.class);
            CourseSimpleInfoDTO cInfo = map.get(r.getCourseId());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setCourseName(cInfo.getName());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }
        return PageDTO.of(page,list);

    }




    @Override
    public void createLearningPlan(LearningPlanDTO dto) {
    // 获取当前登录用户id
        Long userId = UserContext.getUser();
    // 查询课表learning_Lesson 条件user_id course_id
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .one();
        if (lesson == null) {
            throw new BizIllegalException("该课程没有加入课表");
        }
    // 修改课表
    // lesson.setWeekFreq(dto.getFreq());
    // lesson.setPlanStatus(PlanStatus.PLAN_RUNNING);
    // this.updateById(lesson);
    // 链式
        this.lambdaUpdate()
                .set(LearningLesson::getWeekFreq, dto.getFreq())
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        LearningPlanPageVO result = new LearningPlanPageVO();
     //1.查询当前登录用户
        Long userId = UserContext.getUser();
     //2.获取本周登录起始时间
        LocalDate now = LocalDate.now();
      LocalDateTime begin = DateUtils.getWeekBeginTime(now);
      LocalDateTime end = DateUtils.getWeekEndTime(now);

     //3.查询总的统计数据
        //3.1查询本周总的已学习小节数量
        Integer weekFinished =  recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId,userId)
                .eq(LearningRecord::getFinished,true)
                .gt(LearningRecord::getFinishTime,begin)
                .lt(LearningRecord::getFinishTime,end)
        );
        result.setWeekFinished(weekFinished);
        //3.2本周总的计划学习小节数量
        Integer weekTotalPlan  = getBaseMapper().queryTotalPlan(userId);
        result.setWeekTotalPlan(weekTotalPlan);
        //todo 3.3本周学习积分
     //4.查询分页数据
        //4.1分页查询课表信息以及学习计划信息
        Page<LearningLesson> p = lambdaQuery()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus,LessonStatus.NOT_BEGIN,LessonStatus.LEARNING)
                .page(query.toMpPage("latest_learn_time",false));
        List<LearningLesson> records = p.getRecords();
        if(CollUtils.isEmpty(records)){
            return result;
        }
        //4.2查询课表对应的课程信息
        Map<Long,CourseSimpleInfoDTO> cMap= queryCourseSimpleInfoList(records);
        //4.3统计每个课程本周已学习小节数量
        //4.4组装数据vo
        return null;
    }
    private Map<Long,CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records){
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if(cInfoList.isEmpty()){
            throw new BadRequestException("课程信息不存在");
        }
        //3.3 课程号和课程名处理成map
        return cInfoList
                .stream()
                .collect(
                        Collectors.toMap(CourseSimpleInfoDTO::getId,c->c)
                );
    }


    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId){
        //获取用户登录
        Long userId = UserContext.getUser();
        //根据用户登录，课程id 查询课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getCourseId,courseId)
                .eq(LearningLesson::getUserId,userId)
                .one();

        //判空
        if (lesson==null){
            return null;
        }

        //封装 VO
        LearningLessonVO vo = BeanUtils.copyBean(lesson,LearningLessonVO.class);

        return vo;
    }
    @Override
    public  LearningLessonVO queryMyCurrentCourse(){
        Long userId = UserContext.getUser();
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getStatus,LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson==null){
            return null;
        }
        Long courseId = lesson.getCourseId();
        LearningLessonVO vo = BeanUtils.copyBean(lesson,LearningLessonVO.class);
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(courseId,false,false);
        if (cInfo == null) {
            throw new BizIllegalException("课程不存在");
        }
        //获取已报名的课程数量
        Integer count = this.lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        vo.setCourseAmount(count);
        Long latestSection = lesson.getLatestSectionId();
        List<CataSimpleInfoDTO> cataInfo = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSection));
        if (CollUtils.isEmpty(cataInfo)) {
            throw new BizIllegalException("小节不存在");
        }
        vo.setCourseName(cInfo.getName());
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        CataSimpleInfoDTO cataSimpleInfoDTO = cataInfo.get(0);
        vo.setLatestSectionName(cataSimpleInfoDTO.getName());
        vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());
        return vo;
    }

    @Override
    public void deleteCourseFromLesson(Long userId, Long courseId){
       /* QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id",userId);
        wrapper.eq("course_id",courseId);
        this.remove(wrapper);
        */
        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<LearningLesson>()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getCourseId,courseId);
        this.remove(wrapper);
    }

    @Override
    public Integer queryCountByCourseId(Long courseId){
        return lambdaQuery().eq(LearningLesson::getCourseId, courseId).count();
    };
}
