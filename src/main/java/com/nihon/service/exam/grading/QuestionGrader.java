package com.nihon.service.exam.grading;

import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;

/**
 * Giao diện bộ chấm điểm câu hỏi thi theo từng dạng (F40 - F45).
 */
public interface QuestionGrader {

    /**
     * Dạng câu hỏi mà bộ chấm này hỗ trợ.
     */
    ExamQuestionType supportsType();

    /**
     * Chấm điểm câu trả lời của thí sinh dựa trên cấu hình câu hỏi và các khóa đáp án.
     *
     * @param question      Câu hỏi cần chấm
     * @param studentAnswer Câu trả lời của thí sinh (có thể là null hoặc chuỗi rỗng)
     * @return Kết quả chấm điểm chi tiết (điểm, đúng/sai, giải thích)
     */
    GradingResult grade(ExamQuestion question, String studentAnswer);
}

