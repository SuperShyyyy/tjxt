package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.SignRecordVO;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.SignInMessage;

public interface ISignRecordService extends IService<SignRecordVO> {
    SignResultVO addSignRecords();

    Byte[] querySignRecords();


        //增加积分


}
