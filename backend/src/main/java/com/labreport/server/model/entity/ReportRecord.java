package com.labreport.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("report_record")
public class ReportRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String taskId;
    private String status;
    private String outputFilePath;
    private Long outputFileSize;
    private String errorMessage;
    private Integer progressPhase;
    private String phaseName;
    private String evidenceSummary;
    private String validationJson;
    private LocalDateTime generatedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
