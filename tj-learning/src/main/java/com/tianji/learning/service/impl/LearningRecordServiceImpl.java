package com.tianji.learning.service.impl;

import cn.hutool.db.DbRuntimeException;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-02-23
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {
    private final LearningLessonServiceImpl lessonService;
    private final CourseClient courseClient;


    @Override
    @Transactional
    public LearningLessonDTO queryMyLearningRecord(Long courseId){
        Long userId = UserContext.getUser();
        LearningLesson lesson = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getCourseId,courseId)
                .one();
        if (lesson==null){
            throw new DbException("未查找到课程");
        }
        List<LearningRecord> recordList = this.lambdaQuery()
                .eq(LearningRecord::getLessonId,lesson.getId())
                .eq(LearningRecord::getUserId,userId)
                .list();
        //封装vo
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        List<LearningRecordDTO> dtoList = BeanUtils.copyList(recordList,LearningRecordDTO.class);
        dto.setRecords(dtoList);
        return dto;
    }


    @Override
    public void addLearningRecord(LearningRecordFormDTO dto){
        Long userId = UserContext.getUser();
        boolean isFinished = false;
        if (dto.getSectionType().equals(SectionType.VIDEO)) {
            // 提交视频播放记录
            isFinished = handleVideoRecord(userId, dto);
        } else {
            // 提交考试记录
            isFinished = handleExamRecord(userId, dto);
        }
        // 处理课表数据
        handleLessonData(dto, isFinished);
    }



    // 处理课表相关数据
    private void handleLessonData(LearningRecordFormDTO dto, boolean isFinished) {
        // 查询课表数据 learning_lesson  条件 主键id
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课表不存在");
        }
        // 判断是否是第一次学完 isFinished是不是true
        boolean allFinished = false;
        if (isFinished) {
            // 远程调用课程服务 得到课程信息 总小节数
            CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cinfo == null) {
                throw new BizIllegalException("课程不存在");
            }
            Integer sectionNum = cinfo.getSectionNum();// 该课程下小节总数
            // isFinished为true 本小节是第一次学完 判断该用户对该课程下全部小节是否已学完
            Integer learnedSections = lesson.getLearnedSections();// 对课程已学的小节数
            allFinished = learnedSections + 1 >= sectionNum;// 所有课程已学完
        }
        // 更新课表数据
        lessonService.lambdaUpdate()
                // 逻辑 1：只有当前是未开始状态 且确实产生了学习行为（isFinished 或 moment 增加）才改状态为学习中
                .setSql(lesson.getStatus() == LessonStatus.NOT_BEGIN,
                        "status = " + LessonStatus.LEARNING.getValue())
                // 逻辑 2：如果本次学完了 且满足全课学完条件 则改状态为完成
                .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED)
                // 逻辑 3：关键进度更新
                .set(LearningLesson::getLatestSectionId, dto.getSectionId())
                .set(LearningLesson::getLatestLearnTime, dto.getCommitTime())
                // 逻辑 4：利用数据库自增 彻底解决并发计数不准
                .setSql(isFinished, "learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();

    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        LearningRecord record = BeanUtils.copyBean(dto,LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(dto.getCommitTime());
        boolean result = this.save(record);
        if (!result){
            throw new DbRuntimeException("保存考试记录失败");
        }
        return true;

    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
      //  查询旧的学习记录 learning_record userId lessonId SectionId
         LearningRecord learningRecord = this.lambdaQuery()
                .eq(LearningRecord::getUserId,userId)
                .eq(LearningRecord::getLessonId,dto.getLessonId())
                .eq(LearningRecord::getSectionId,dto.getSectionId())
                .one();
        if(learningRecord==null){
            //旧的学习记录不存在
            LearningRecord record =BeanUtils.copyBean(dto,LearningRecord.class);
            record.setUserId(userId);
            boolean result = this.save(record);
            if (!result){
                throw new DbRuntimeException("保存学习记录失败");
            }
            return false;
        }
        boolean isFinished = !learningRecord.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        boolean result = this.lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(isFinished, LearningRecord::getFinished, true)
                .set(isFinished, LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, learningRecord.getId())
                .update();
        if (!result) {
            throw new DbRuntimeException("更新视频学习记录失败");
        }
        return isFinished;
    }
}