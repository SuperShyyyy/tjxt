package com.tianji.learning.service.impl;

import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.enums.QuestionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-02-23
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {
    private final InteractionQuestionMapper questionMapper;
    @Override
    public void saveReply(ReplyDTO dto){
        // 获取当前登录用户的id
        Long userId = UserContext.getUser();

        // 保存回答或者评论 interaction_reply
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(userId);
        InteractionQuestion question = questionMapper.selectById(dto.getQuestionId());
        if(dto.getAnswerId()!=null){
            //是回复
            InteractionReply answerInfo = this.getById(dto.getAnswerId());
            answerInfo.setReplyTimes(answerInfo.getReplyTimes() + 1);
            this.updateById(answerInfo);
        }
        else{
            //是回答
           question.setLatestAnswerId(reply.getId());
           question.setAnswerTimes(reply.getReplyTimes());
        }
        boolean isStudent = dto.getIsStudent();
        if(isStudent){
            question.setStatus(QuestionStatus.UN_CHECK);
        }
        questionMapper.updateById(question);
    }
}
