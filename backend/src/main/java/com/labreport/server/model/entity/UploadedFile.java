package com.labreport.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("uploaded_file")
public class UploadedFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String originalName;
    private String storedName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String language;
    private String mimeType;
    private String md5Hash;
    @TableField("classified_role")
    private String classifiedRole;
    @TableField("folder_path")
    private String folderPath;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
