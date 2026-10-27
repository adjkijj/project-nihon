package com.nihon.repository;

import com.nihon.entity.KanjiWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy xuất dữ liệu từ vựng cần nhớ của Kanji (bảng kanji_word).
 */
public interface KanjiWordRepository extends JpaRepository<KanjiWord, Long> {

    /**
     * Lấy danh sách từ cần nhớ theo mã Kanji, sắp xếp theo thứ tự sortOrder tăng dần.
     *
     * @param kanjiId mã định danh Kanji
     * @return danh sách từ cần nhớ
     */
    List<KanjiWord> findByKanjiIdOrderBySortOrderAsc(Long kanjiId);

    /**
     * Tìm từ cần nhớ theo mã Kanji và từ tiếng Nhật.
     *
     * @param kanjiId mã định danh Kanji
     * @param japanese từ tiếng Nhật
     * @return từ cần nhớ nếu tồn tại
     */
    Optional<KanjiWord> findByKanjiIdAndJapanese(Long kanjiId, String japanese);

    /**
     * Kiểm tra xem cặp (kanjiId, japanese) đã tồn tại hay chưa.
     *
     * @param kanjiId mã định danh Kanji
     * @param japanese từ tiếng Nhật
     * @return true nếu đã tồn tại
     */
    boolean existsByKanjiIdAndJapanese(Long kanjiId, String japanese);

    /**
     * Xóa toàn bộ từ cần nhớ liên thuộc một Kanji.
     *
     * @param kanjiId mã định danh Kanji
     */
    void deleteByKanjiId(Long kanjiId);
}
