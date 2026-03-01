package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-02-23
 */
@RestController
@RequestMapping("/admin/question")
@RequiredArgsConstructor
@Api(tags = "管理端问题相关接口")
public class InteractionQuestionAdminController {
    private final IInteractionQuestionService questionService;
    @ApiOperation("管理端查询问题接口")
    @GetMapping ("page")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query){
        return questionService.queryQuestionPageAdmin(query);
    }

    @ApiOperation("管理端隐藏问题")
    @PutMapping("/{id}/hidden/{hidden}")
    public void hiddenQuestionAdmin (@PathVariable("id") Long id,@PathVariable("hidden") boolean hidden){
        questionService.hiddenQuestionAdmin(id,hidden);
    }


    @ApiOperation("管理端根据id查询问题详情")
    @GetMapping("{id}")
    public QuestionAdminVO queryQuestionAdmin(@PathVariable("Id") Long id){
        return questionService.queryQuestionAdminById(id);
    }

}
