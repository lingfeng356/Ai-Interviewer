package com.lingfeng.interviewer.questionbank;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 题库文件的顶层结构（对应 resources/question-bank/java-backend.json）。
 */
@Data
@NoArgsConstructor
public class QuestionBank {

    private int version;

    /** 数据来源说明，仅作标注，不参与检索 */
    private String note;

    private List<QuestionItem> questions;
}
