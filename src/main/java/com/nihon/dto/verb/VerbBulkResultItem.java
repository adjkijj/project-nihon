package com.nihon.dto.verb;

import com.nihon.entity.Verb;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả xử lý cho từng dòng trong thao tác thêm động từ hàng loạt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerbBulkResultItem {

    private int lineNumber;
    private String line;
    private boolean success;
    private String message;
    private Verb verb;
}
