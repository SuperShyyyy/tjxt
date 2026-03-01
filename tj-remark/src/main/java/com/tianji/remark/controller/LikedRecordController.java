package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-02-24
 */
@EnableScheduling
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
public class LikedRecordController {
    private final ILikedRecordService likedRecordService;
    @PostMapping
    @ApiOperation("点赞/取消点赞接口")
    public void addLikeRecord(@Valid @RequestBody LikeRecordFormDTO dto){
        likedRecordService.addLikeRecord(dto);
    }

    @GetMapping("list")
    @ApiOperation("查询点赞状态")
    public Set<Long> isBizLiked(@RequestParam("bizIds") List<Long> bizIds){
        return likedRecordService.isBizLiked(bizIds);
    }
}
