package com.labreport.server.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreport.server.model.entity.UploadedFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UploadedFileMapper extends BaseMapper<UploadedFile> {}
