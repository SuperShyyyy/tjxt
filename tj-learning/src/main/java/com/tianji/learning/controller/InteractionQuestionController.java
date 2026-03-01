package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-02-23
 */
@RestController
@RequestMapping("/question")
@RequiredArgsConstructor
@Api(tags = "问题相关接口")
public class InteractionQuestionController {
    private final IInteractionQuestionService questionService;
    private final IInteractionReplyService replyService;
    @ApiOperation("新增问题接口")
    @PostMapping
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionFormDTO){
        questionService.saveQuestion(questionFormDTO);
    }
    @ApiOperation("分页查询问题接口")
    @GetMapping("page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery questionPageQuery){
        return questionService.queryQuestionPage(questionPageQuery);
    }
    @ApiOperation("根据id查询问题详情")
    @GetMapping("{id}")
    public QuestionVO queryQuestionById(@PathVariable("id") Long id){
         return questionService.queryQuestionById(id);
    }

    @ApiOperation("删除我的问题")
    @DeleteMapping("{id}")
    public void deleteMyQuestion(@PathVariable("id") Long id){
        questionService.deleteMyQuestion(id);
    }
    @ApiOperation("新增评论或回答接口")
    @PostMapping
    public void addReplies(@Validated @RequestBody ReplyDTO dto){
        replyService.saveReply(dto);
    }

    @ApiOperation("修改我的问题")
    @PutMapping("{id}")
    public void updateMyQuestion(@PathVariable Long id,QuestionFormDTO questionFormDTO) {
        questionService.updateMyQuestion(id,questionFormDTO);
    }

}
