package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.service.impl.LearningLessonServiceImpl;
import com.tianji.learning.service.impl.LearningRecordServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-02-23
 */
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningRecordController {
    private final LearningRecordServiceImpl recordService;
    /*
    *- 创建学习计划
-   查询学习记录
-   提交学习记录
-   查询我的计划
    * */



    @ApiOperation("查询学习记录")
    @GetMapping("/{courseId}")
    public LearningLessonDTO queryMyLearningRecord(@PathVariable ("courseId") Long courseId){
       return  recordService.queryMyLearningRecord(courseId);
    }

    @ApiOperation("提交学习记录")
    @PostMapping
    public void addLearningRecord(@RequestBody @Validated LearningRecordFormDTO dto){
        recordService.addLearningRecord(dto);
    }


}
