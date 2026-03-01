package com.tianji.remark.service.impl;

import com.tianji.remark.domain.po.PointsRecord;
import com.tianji.remark.mapper.PointsRecordMapper;
import com.tianji.remark.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-02-25
 */
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

}
