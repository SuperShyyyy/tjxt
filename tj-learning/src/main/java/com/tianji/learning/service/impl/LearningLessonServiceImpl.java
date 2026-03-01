package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
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
    private LearningRecordMapper recordMapper;


    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds){
        //1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cInfoList)){
            log.error("课程信息不存在,无法添加到课表");
            return;
        }
        //2.循环遍历 处理learningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for(CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            //2.1获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if(validDuration != null && validDuration > 0 ){
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            //2.2填充userid和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
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
    //todo
    @Override
   public  LearningLesson queryByUserIdAndCourseId(Long userId,Long courseId){
        return getOne(buildUserIdAndCourseIdWrapper(userId,courseId));
    }
    @Override
    public void createLearningPlan(@NotNull @Min(1) Long courseId, @NotNull @Range(min = 1, max = 50) Integer freq){
       //获取登录用户
        Long userId = UserContext.getUser();
        //1.查询课表指定课程有关数据
        LearningLesson lesson =  queryByUserIdAndCourseId(userId,courseId);
        AssertUtils.isNotNull(lesson,"课程信息不存在");
        //2.修改数据
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if (lesson.getPlanStatus()==PlanStatus.NO_PLAN){
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);

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
}
