package com.nihon.service.exam;

import com.nihon.dto.exam.ExamExcelRow;
import com.nihon.dto.exam.ExamImportPreviewReport;
import com.nihon.entity.ExamPaper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class Particles50SampleImportTest {

    private static final Path SAMPLE_FILE_PATH = Paths.get("docs", "samples", "particles_50.xlsx");

    @Autowired
    private ExamExcelService examExcelService;

    @Autowired
    private ExamService examService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    public record ParticleQuestionDef(
            String promptJa,
            String promptVi,
            String choice1,
            String choice2,
            String choice3,
            String choice4,
            String correctAnswer,
            String explanation,
            String targetParticle
    ) {}

    public static final List<ParticleQuestionDef> SAMPLE_QUESTIONS = List.of(
            // --- Trợ từ を (1-7) ---
            new ParticleQuestionDef(
                    "毎朝、近くの公園（　）30分ほど散歩しています。",
                    "Mỗi sáng, tôi đi dạo khoảng 30 phút trong công viên gần nhà.",
                    "で", "を", "に", "へ",
                    "2",
                    "Động từ chuyển động 散歩する (đi dạo) đi với trợ từ を để chỉ không gian di chuyển xuyên qua.",
                    "を"
            ),
            new ParticleQuestionDef(
                    "次の大きな交差点（　）右へ曲がってください。",
                    "Xin hãy rẽ phải ở ngã tư lớn tiếp theo.",
                    "を", "で", "に", "は",
                    "1",
                    "Trợ từ を đi với động từ 曲がる (rẽ) để chỉ vị trí diễn ra sự chuyển hướng di chuyển.",
                    "を"
            ),
            new ParticleQuestionDef(
                    "毎朝8時にアパート（　）出て、地下鉄の駅に向かいます。",
                    "Mỗi sáng tôi rời khỏi căn hộ lúc 8 giờ rồi đi về phía ga tàu điện ngầm.",
                    "に", "で", "を", "から",
                    "3",
                    "Trợ từ を kết hợp với động từ 出る (rời khỏi) để chỉ nơi xuất phát rời đi.",
                    "を"
            ),
            new ParticleQuestionDef(
                    "市役所前のバス停でバス（　）降りました。",
                    "Tôi đã xuống xe buýt tại trạm dừng trước tòa thị chính.",
                    "に", "を", "で", "が",
                    "2",
                    "Trợ từ を dùng với 降りる (xuống xe) để chỉ phương tiện giao thông được rời xuống.",
                    "を"
            ),
            new ParticleQuestionDef(
                    "喉がとても渇きましたから、冷たい麦茶（　）飲みましょう。",
                    "Vì rất khát nước nên chúng ta cùng uống trà lúa mạch mát lạnh nhé.",
                    "が", "に", "で", "を",
                    "4",
                    "Trợ từ を đánh dấu tân ngữ trực tiếp chịu tác động của ngoại động từ 飲む (uống).",
                    "を"
            ),
            new ParticleQuestionDef(
                    "工事中で危ないですから、あの古い橋（　）渡らないでください。",
                    "Vì đang thi công rất nguy hiểm nên xin đừng đi qua cây cầu cũ kia.",
                    "で", "に", "を", "へ",
                    "3",
                    "Trợ từ を kết hợp với động từ di chuyển 渡る (băng qua) để chỉ đoạn đường, cây cầu đi xuyên qua.",
                    "を"
            ),
            new ParticleQuestionDef(
                    "週末は部屋の掃除（　）してから、スーパーへ買い物に行きます。",
                    "Cuối tuần tôi dọn dẹp phòng xong rồi mới đi siêu thị mua sắm.",
                    "を", "が", "で", "に",
                    "1",
                    "Trợ từ を đi với danh động từ 掃除（を）する để chỉ đối tượng được dọn dẹp.",
                    "を"
            ),

            // --- Trợ từ が (8-15) ---
            new ParticleQuestionDef(
                    "窓の外は冷たい風（　）びゅうびゅう吹いていますね。",
                    "Bên ngoài cửa sổ gió lạnh đang thổi vù vù nhỉ.",
                    "は", "が", "を", "に",
                    "2",
                    "Trợ từ が dùng để biểu thị chủ ngữ của hiện tượng tự nhiên khách quan (gió thổi).",
                    "が"
            ),
            new ParticleQuestionDef(
                    "私は辛すぎるエスニック料理（　）あまり好きではありません。",
                    "Tôi không thích các món ăn dân tộc quá cay cho lắm.",
                    "を", "は", "が", "に",
                    "3",
                    "Đối tượng của tính từ biểu thị cảm xúc, sở thích như 好き/嫌い đi với trợ từ が.",
                    "が"
            ),
            new ParticleQuestionDef(
                    "山田さんはピアノやギターを上手に弾くこと（　）できます。",
                    "Anh Yamada có thể chơi thành thạo đàn piano và đàn guitar.",
                    "を", "に", "で", "が",
                    "4",
                    "Cấu trúc biểu thị năng lực 〜ことができる (có thể làm gì) sử dụng trợ từ が trước できます.",
                    "が"
            ),
            new ParticleQuestionDef(
                    "すみません、この掲示板の漢字の読み方（　）よく分かりません。",
                    "Xin lỗi, tôi không hiểu rõ cách đọc của chữ Hán trên bảng thông báo này.",
                    "が", "を", "で", "に",
                    "1",
                    "Đối tượng của động từ tri nhận, nhận thức 分かる (hiểu) luôn đi cùng trợ từ が.",
                    "が"
            ),
            new ParticleQuestionDef(
                    "居間の丸いテーブルの上に新しい辞書（　）置いてあります。",
                    "Trên chiếc bàn tròn ở phòng khách có đặt sẵn cuốn từ điển mới.",
                    "を", "が", "に", "で",
                    "2",
                    "Cấu trúc 〜てある biểu thị trạng thái kết quả của hành động có chủ ý, tân ngữ chuyển thành chủ ngữ với trợ từ が.",
                    "が"
            ),
            new ParticleQuestionDef(
                    "私たちの町の駅前に大きな映画館（　）できました。",
                    "Trước nhà ga thị trấn của chúng tôi mới xây xong một rạp chiếu phim lớn.",
                    "を", "に", "が", "で",
                    "3",
                    "Động từ できる mang ý nghĩa hoàn thành, được xây dựng/mở ra, chủ ngữ đi cùng trợ từ が.",
                    "が"
            ),
            new ParticleQuestionDef(
                    "今日は朝から少し頭（　）痛いので、薬を飲んで休みます。",
                    "Hôm nay từ sáng đầu tôi đã hơi đau nên tôi sẽ uống thuốc rồi nghỉ ngơi.",
                    "は", "を", "に", "が",
                    "4",
                    "Cấu trúc miêu tả tình trạng bệnh lý cơ thể (bộ phận + が + 痛い) sử dụng trợ từ が.",
                    "が"
            ),
            new ParticleQuestionDef(
                    "この店でどのカバン（　）一番軽くて使いやすいですか。",
                    "Trong cửa hàng này chiếc túi nào nhẹ nhất và dễ dùng nhất vậy?",
                    "が", "は", "を", "で",
                    "1",
                    "Khi từ để hỏi nghi vấn từ (どのカバン) làm chủ ngữ trong câu thì bắt buộc phải dùng trợ từ が.",
                    "が"
            ),

            // --- Trợ từ に (16-23) ---
            new ParticleQuestionDef(
                    "私は毎朝6時半（　）起きて、日本語のラジオを聞きます。",
                    "Mỗi sáng tôi thức dậy lúc 6 giờ rưỡi rồi nghe đài tiếng Nhật.",
                    "で", "に", "を", "へ",
                    "2",
                    "Trợ từ に đứng sau mốc thời gian có con số cụ thể (6時半) để chỉ thời điểm diễn ra hành động.",
                    "に"
            ),
            new ParticleQuestionDef(
                    "日曜日に駅前のショッピングモールへ靴を買い（　）行きました。",
                    "Vào chủ nhật tôi đã đến trung tâm mua sắm trước ga để mua giày.",
                    "で", "を", "に", "へ",
                    "3",
                    "Mẫu câu chỉ mục đích di chuyển: Động từ thể ます bỏ ます + に + 行く/来る/帰る.",
                    "に"
            ),
            new ParticleQuestionDef(
                    "来年の4月に家族と一緒に京都（　）引っ越す予定です。",
                    "Vào tháng 4 năm sau tôi dự định sẽ chuyển nhà đến Kyoto cùng với gia đình.",
                    "で", "を", "から", "に",
                    "4",
                    "Trợ từ に chỉ điểm đến, đích đến nhập vào của hành động chuyển nơi ở (引っ越す).",
                    "に"
            ),
            new ParticleQuestionDef(
                    "昨日の午後、駅の近くのカフェで高校時代の親友（　）会いました。",
                    "Chiều hôm qua, tôi đã gặp người bạn thân thời cấp ba ở quán cà phê gần nhà ga.",
                    "に", "を", "で", "へ",
                    "1",
                    "Động từ 会う (gặp gỡ) đi cùng trợ từ に để chỉ đối tượng được gặp.",
                    "に"
            ),
            new ParticleQuestionDef(
                    "健康を維持するために、1か月（　）4回プールで泳ぎます。",
                    "Để duy trì sức khỏe, một tháng tôi bơi ở hồ bơi 4 lần.",
                    "で", "に", "を", "の",
                    "2",
                    "Cấu trúc biểu thị tần suất thực hiện hành vi: khoảng thời gian + に + số lần (1か月に4回).",
                    "に"
            ),
            new ParticleQuestionDef(
                    "私の姉は今、市立病院（　）臨床検査技師として勤めています。",
                    "Chị gái tôi hiện đang làm việc với tư cách là kỹ thuật viên xét nghiệm tại bệnh viện thành phố.",
                    "で", "を", "に", "へ",
                    "3",
                    "Động từ 勤める (làm việc, phụng sự tại một tổ chức cơ quan) đi với trợ từ に chỉ cơ quan trực thuộc.",
                    "に"
            ),
            new ParticleQuestionDef(
                    "自分の部屋の白い壁（　）日本旅行の写真のポスターを貼りました。",
                    "Tôi đã dán tấm áp phích hình ảnh chuyến du lịch Nhật Bản lên bức tường trắng trong phòng mình.",
                    "で", "を", "へ", "に",
                    "4",
                    "Trợ từ に biểu thị bề mặt tiếp nhận đồ vật gắn kết vào (bức tường + に + dán).",
                    "に"
            ),
            new ParticleQuestionDef(
                    "授業のあとで何かわからないことがあれば、先生（　）質問してください。",
                    "Sau giờ học nếu có điều gì không hiểu, xin hãy đặt câu hỏi cho giáo viên.",
                    "に", "を", "で", "から",
                    "1",
                    "Trợ từ に chỉ đối tượng tiếp nhận thông tin hoặc người được hướng tới để hỏi (先生に質問する).",
                    "に"
            ),

            // --- Trợ từ で (24-31) ---
            new ParticleQuestionDef(
                    "昨日の放課後は市民図書館（　）静かに読書をしました。",
                    "Chiều hôm qua sau giờ học, tôi đã đọc sách yên tĩnh tại thư viện thành phố.",
                    "に", "で", "を", "へ",
                    "2",
                    "Trợ từ で dùng để chỉ địa điểm cụ thể diễn ra một hành động chủ động (đọc sách tại thư viện).",
                    "で"
            ),
            new ParticleQuestionDef(
                    "毎日の通勤はとても便利なので、いつも地下鉄（　）通っています。",
                    "Vì đi làm hàng ngày rất thuận tiện nên tôi luôn đi làm bằng tàu điện ngầm.",
                    "に", "を", "で", "へ",
                    "3",
                    "Trợ từ で chỉ phương tiện giao thông được sử dụng để di chuyển (bằng tàu điện ngầm).",
                    "で"
            ),
            new ParticleQuestionDef(
                    "試験の答案用紙には鉛筆ではなく黒のペン（　）記入してください。",
                    "Trên phiếu trả lời bài thi, xin hãy viết bằng bút mực đen chứ không dùng bút chì.",
                    "に", "を", "と", "で",
                    "4",
                    "Trợ từ で chỉ công cụ, dụng cụ được dùng để thực hiện hành động viết (viết bằng bút).",
                    "で"
            ),
            new ParticleQuestionDef(
                    "急な激しい腹痛（　）昨日の午後の会議を欠席してしまいました。",
                    "Vì cơn đau bụng dữ dội bất ngờ nên tôi đã lỡ vắng mặt trong cuộc họp chiều hôm qua.",
                    "で", "に", "を", "から",
                    "1",
                    "Trợ từ で chỉ nguyên nhân, lý do khách quan (đau ốm, thiên tai, trở ngại khách quan).",
                    "で"
            ),
            new ParticleQuestionDef(
                    "ベトナムの伝統料理フォーの麺は米の粉（　）作られています。",
                    "Sợi bánh phở của món ăn truyền thống Việt Nam được làm từ bột gạo.",
                    "から", "で", "に", "を",
                    "2",
                    "Trợ từ で dùng để chỉ chất liệu hữu hình làm nên vật thể khi mắt thường vẫn phân biệt được chất liệu.",
                    "で"
            ),
            new ParticleQuestionDef(
                    "日本の四季の季節の中（　）私は秋の紅葉が一番好きです。",
                    "Trong bốn mùa của Nhật Bản, tôi thích nhất là mùa lá đỏ của mùa thu.",
                    "に", "は", "で", "を",
                    "3",
                    "Cấu trúc xác định phạm vi lựa chọn so sánh nhất: danh từ phạm vi + の中で...が一番.",
                    "で"
            ),
            new ParticleQuestionDef(
                    "八百屋で新鮮なトマトが5個（　）300円で売られていました。",
                    "Ở tiệm rau, cà chua tươi được bán 5 quả với giá 300 yên.",
                    "に", "を", "は", "で",
                    "4",
                    "Trợ từ で dùng sau số lượng/mức giá để biểu thị giới hạn tổng số lượng được tính gộp (5 quả là...).",
                    "で"
            ),
            new ParticleQuestionDef(
                    "会議で話し合った大切な要点は簡単なメモ（　）残しておきましょう。",
                    "Chúng ta hãy lưu lại những điểm cốt lõi đã thảo luận trong cuộc họp bằng bản ghi chú ngắn gọn nhé.",
                    "で", "に", "を", "へ",
                    "1",
                    "Trợ từ で biểu thị hình thức, phương thức thực hiện hành động (ghi lại bằng hình thức ghi chú).",
                    "で"
            ),

            // --- Trợ từ は (32-36) ---
            new ParticleQuestionDef(
                    "私は生魚の刺身は好きですが、納豆（　）どうしても食べられません。",
                    "Tôi thích món cá sống sashimi, nhưng món natto thì dù thế nào tôi cũng không thể ăn được.",
                    "を", "は", "が", "に",
                    "2",
                    "Trợ từ は dùng trong vế tương phản đối chiếu giữa hai sự vật (món này thì thích nhưng món kia thì không).",
                    "は"
            ),
            new ParticleQuestionDef(
                    "今日（　）空がとても青くて晴れていますから、散歩に行きましょう。",
                    "Hôm nay bầu trời rất xanh và quang đãng nên chúng ta hãy đi dạo nhé.",
                    "が", "を", "は", "に",
                    "3",
                    "Trợ từ は đưa từ chỉ thời gian 'hôm nay' (今日) lên làm chủ đề chính của câu nhận xét.",
                    "は"
            ),
            new ParticleQuestionDef(
                    "この古い町には歴史的な寺（　）多いですが、近代的なビルはありません。",
                    "Ở thị trấn cổ này chùa chiền mang tính lịch sử thì nhiều, nhưng tòa nhà hiện đại thì không có.",
                    "が", "を", "で", "は",
                    "4",
                    "Trợ từ は dùng để nhấn mạnh sự đối lập giữa hai vế câu (chùa thì nhiều nhưng tòa nhà hiện đại thì không có).",
                    "は"
            ),
            new ParticleQuestionDef(
                    "私の弟（　）今年の春に専門学校を卒業して貿易会社に就職しました。",
                    "Em trai tôi vào mùa xuân năm nay đã tốt nghiệp trường chuyên môn và vào làm tại một công ty thương mại.",
                    "は", "が", "を", "に",
                    "1",
                    "Trợ từ は đánh dấu chủ ngữ đóng vai trò là chủ đề xuyên suốt của câu trần thuật (em trai tôi thì...).",
                    "は"
            ),
            new ParticleQuestionDef(
                    "マイさんはベトナム語と英語が話せますが、日本語（　）まだ話せません。",
                    "Bạn Mai nói được tiếng Việt và tiếng Anh, nhưng tiếng Nhật thì vẫn chưa nói được.",
                    "を", "は", "が", "に",
                    "2",
                    "Trợ từ は thay thế cho trợ từ が trong câu phủ định năng lực để nhấn mạnh tính đối chiếu tương phản.",
                    "は"
            ),

            // --- Trợ từ へ (37-41) ---
            new ParticleQuestionDef(
                    "明日の早朝に新幹線で大阪の支社（　）出張することになりました。",
                    "Tôi được quyết định sẽ đi công tác đến chi nhánh Osaka bằng tàu Shinkansen vào sáng sớm mai.",
                    "で", "を", "へ", "から",
                    "3",
                    "Trợ từ へ chỉ phương hướng di chuyển hướng tới một địa điểm đích đến (hướng tới chi nhánh Osaka).",
                    "へ"
            ),
            new ParticleQuestionDef(
                    "日本（　）留学するために、毎日アルバイトをしながら日本語学校に通っています。",
                    "Để đi du học sang Nhật Bản, tôi vừa làm thêm vừa theo học tại trường Nhật ngữ mỗi ngày.",
                    "で", "を", "から", "へ",
                    "4",
                    "Trợ từ へ chỉ hướng di chuyển tới đất nước mục tiêu (du học sang nước Nhật).",
                    "へ"
            ),
            new ParticleQuestionDef(
                    "田舎の故郷に住んでいる両親（　）感謝の手紙と贈り物を送りました。",
                    "Tôi đã gửi thư cảm ơn và quà tặng tới bố mẹ đang sống ở quê nhà nông thôn.",
                    "へ", "で", "を", "から",
                    "1",
                    "Trợ từ へ chỉ phương hướng tiếp nhận ở phương xa mà lá thư và bưu phẩm hướng tới.",
                    "へ"
            ),
            new ParticleQuestionDef(
                    "初めての空港で迷わないように、中央の案内所（　）向かって歩きました。",
                    "Để không bị lạc ở sân bay lần đầu tới, tôi đã bước đi hướng về phía quầy thông tin trung tâm.",
                    "で", "へ", "を", "から",
                    "2",
                    "Động từ 向かう (hướng về phía) kết hợp với trợ từ へ để biểu thị phương hướng di chuyển.",
                    "へ"
            ),
            new ParticleQuestionDef(
                    "外が薄暗くなってきたので、遊んでいた子供たちは自分の家（　）帰っていきました。",
                    "Vì ngoài trời đã bắt đầu mờ tối nên bọn trẻ đang chơi đùa đã cùng quay trở về nhà của mình.",
                    "で", "を", "へ", "から",
                    "3",
                    "Trợ từ へ chỉ phương hướng quay trở về nơi chốn xuất phát điểm (trở về nhà).",
                    "へ"
            ),

            // --- Trợ từ と (42-45) ---
            new ParticleQuestionDef(
                    "今度の三連休に会社の仲の良い同僚（　）北アルプスへ登山に行きます。",
                    "Vào kỳ nghỉ ba ngày tới, tôi sẽ đi leo núi dãy Alps phía Bắc cùng những đồng nghiệp thân thiết trong công ty.",
                    "に", "で", "を", "と",
                    "4",
                    "Trợ từ と biểu thị đối tượng cùng tham gia thực hiện hành động (cùng với đồng nghiệp).",
                    "と"
            ),
            new ParticleQuestionDef(
                    "私の普段の朝食は焼き魚（　）みそ汁と温かいご飯です。",
                    "Bữa sáng thường ngày của tôi là cá nướng cùng súp miso và cơm nóng.",
                    "と", "に", "で", "や",
                    "1",
                    "Trợ từ と dùng để liên kết các danh từ theo cách liệt kê đầy đủ tất cả các thành phần.",
                    "と"
            ),
            new ParticleQuestionDef(
                    "私は大学を卒業したら、海外で自分の店を持ちたい（　）考えています。",
                    "Tôi đang suy nghĩ rằng sau khi tốt nghiệp đại học muốn mở một cửa hàng của riêng mình ở nước ngoài.",
                    "を", "と", "に", "で",
                    "2",
                    "Trợ từ と đóng vai trò là trợ từ trích dẫn nội dung ý định hoặc suy nghĩ trước 考える hoặc 思う.",
                    "と"
            ),
            new ParticleQuestionDef(
                    "スマートフォンの画面が壊れてしまったので、修理の専門家（　）相談しました。",
                    "Vì màn hình điện thoại thông minh bị hỏng nên tôi đã trao đổi, thảo luận với chuyên gia sửa chữa.",
                    "を", "で", "と", "へ",
                    "3",
                    "Động từ 相談する (thảo luận, bàn bạc qua lại hai chiều) đi với trợ từ と chỉ người cùng trao đổi.",
                    "と"
            ),

            // --- Trợ từ から (46-49) ---
            new ParticleQuestionDef(
                    "昨夜は強い台風が通過した（　）、今朝は電車のダイヤが乱れています。",
                    "Đêm qua vì có một cơn bão mạnh quét qua nên sáng nay lịch trình tàu điện đang bị xáo trộn.",
                    "まで", "に", "で", "から",
                    "4",
                    "Trợ từ liên từ から đứng sau mệnh đề thể thông thường để nêu lý do, nguyên nhân (vì có bão mạnh quét qua nên...).",
                    "から"
            ),
            new ParticleQuestionDef(
                    "私たちの通う専門学校の新学期は4月の第2週（　）始まります。",
                    "Học kỳ mới của trường chuyên môn mà chúng tôi theo học bắt đầu từ tuần thứ hai của tháng 4.",
                    "から", "まで", "に", "で",
                    "1",
                    "Trợ từ から chỉ thời điểm bắt đầu của một khoảng thời gian, sự kiện hoặc kỳ học (từ tuần thứ hai).",
                    "から"
            ),
            new ParticleQuestionDef(
                    "海を越えて遠いベトナム（　）日本へ来てくださって、本当に感謝しています。",
                    "Bạn đã vượt biển từ đất nước Việt Nam xa xôi đến với Nhật Bản, tôi thật sự rất cảm kích.",
                    "まで", "から", "に", "へ",
                    "2",
                    "Trợ từ から chỉ điểm xuất phát, nguồn gốc không gian địa lý của hành trình di chuyển (đến từ Việt Nam).",
                    "から"
            ),
            new ParticleQuestionDef(
                    "日本の伝統的な調味料のしょうゆは大豆と小麦（　）造られます。",
                    "Nước tương xì dầu, gia vị truyền thống của Nhật Bản, được sản xuất từ đậu nành và lúa mì.",
                    "で", "に", "から", "を",
                    "3",
                    "Trợ từ から biểu thị nguyên liệu ban đầu đã trải qua quá trình lên men, biến đổi chất sâu sắc thành sản phẩm mới.",
                    "から"
            ),

            // --- Trợ từ まで (50) ---
            new ParticleQuestionDef(
                    "明日の日本語能力試験の直前復習のため、昨夜は深夜の3時（　）起きていました。",
                    "Để ôn tập cấp tốc trước kỳ thi năng lực tiếng Nhật ngày mai, đêm qua tôi đã thức đến tận 3 giờ đêm.",
                    "から", "に", "で", "まで",
                    "4",
                    "Trợ từ まで chỉ giới hạn mốc thời gian kết thúc của trạng thái duy trì liên tục (thức đến 3 giờ đêm).",
                    "まで"
            )
    );

    @BeforeAll
    static void ensureSampleExcelFileExists() throws IOException {
        Files.createDirectories(SAMPLE_FILE_PATH.getParent());
        byte[] excelBytes = buildSampleWorkbookBytes();
        Files.write(SAMPLE_FILE_PATH, excelBytes);
    }

    public static byte[] buildSampleWorkbookBytes() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Exam");
            sheet.setDisplayGridlines(true);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setFontName("Segoe UI");
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle dataStyle = workbook.createCellStyle();
            Font dataFont = workbook.createFont();
            dataFont.setFontName("Segoe UI");
            dataFont.setFontHeightInPoints((short) 10);
            dataStyle.setFont(dataFont);

            String[] headers = {
                    "Code đề", "Tên đề", "Thời lượng (phút)", "Loại đề", "Phần",
                    "Loại câu hỏi", "Đề bài tiếng Nhật", "Đề bài tiếng Việt", "Điểm",
                    "Đáp án 1", "Đáp án 2", "Đáp án 3", "Đáp án 4",
                    "Đáp án đúng", "Giải thích"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < SAMPLE_QUESTIONS.size(); i++) {
                ParticleQuestionDef q = SAMPLE_QUESTIONS.get(i);
                Row row = sheet.createRow(i + 1);
                row.setHeightInPoints(20);

                // Dòng đầu tiên điền đầy đủ metadata đề thi, các dòng sau kế thừa
                if (i == 0) {
                    createCell(row, 0, "PARTICLES-A2-50", dataStyle);
                    createCell(row, 1, "50 Câu Luyện Tập Trợ Từ Tiếng Nhật Trình Độ A2", dataStyle);
                    createCell(row, 2, "45", dataStyle);
                    createCell(row, 3, "REVIEW", dataStyle);
                    createCell(row, 4, "Phần 1: Trợ từ tiếng Nhật A2 (は・が・を・に・で・へ・と・から・まで)", dataStyle);
                } else {
                    createCell(row, 0, "", dataStyle);
                    createCell(row, 1, "", dataStyle);
                    createCell(row, 2, "", dataStyle);
                    createCell(row, 3, "", dataStyle);
                    createCell(row, 4, "", dataStyle);
                }

                createCell(row, 5, "MULTIPLE_CHOICE", dataStyle);
                createCell(row, 6, q.promptJa(), dataStyle);
                createCell(row, 7, q.promptVi(), dataStyle);
                createCell(row, 8, "1.0", dataStyle);
                createCell(row, 9, q.choice1(), dataStyle);
                createCell(row, 10, q.choice2(), dataStyle);
                createCell(row, 11, q.choice3(), dataStyle);
                createCell(row, 12, q.choice4(), dataStyle);
                createCell(row, 13, q.correctAnswer(), dataStyle);
                createCell(row, 14, q.explanation(), dataStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 3500));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private static void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    @Test
    @DisplayName("Tệp mẫu particles_50.xlsx tồn tại, có đúng 50 câu và đủ 9 loại trợ từ theo yêu cầu")
    void testSampleFileDefinitionAndParticlesCoverage() throws IOException {
        assertThat(Files.exists(SAMPLE_FILE_PATH))
                .as("Tệp Excel mẫu phải tồn tại tại docs/samples/particles_50.xlsx")
                .isTrue();

        assertThat(Files.size(SAMPLE_FILE_PATH))
                .as("Tệp Excel mẫu không được rỗng")
                .isGreaterThan(2000L);

        byte[] bytes = Files.readAllBytes(SAMPLE_FILE_PATH);
        ExamImportPreviewReport report = examExcelService.parseAndValidateWorkbook(bytes, "test-token", "particles_50.xlsx");

        assertThat(report).isNotNull();
        assertThat(report.getGlobalErrors()).as("Không được có lỗi toàn cục").isEmpty();
        assertThat(report.getErrors()).as("Không được có lỗi hàng nào").isEmpty();
        assertThat(report.isValid()).as("Báo cáo import phải hoàn toàn hợp lệ").isTrue();
        assertThat(report.getQuestionCount()).isEqualTo(50);
        assertThat(report.getPaperCode()).isEqualTo("PARTICLES-A2-50");
        assertThat(report.getPaperTitle()).isEqualTo("50 Câu Luyện Tập Trợ Từ Tiếng Nhật Trình Độ A2");
        assertThat(report.getCategory()).isEqualTo("REVIEW");
        assertThat(report.getDurationMinutes()).isEqualTo(45);

        // Kiểm tra từng câu hỏi chi tiết
        Set<String> coveredParticles = new HashSet<>();
        List<String> requiredParticles = List.of("は", "が", "を", "に", "で", "へ", "と", "から", "まで");

        for (int i = 0; i < report.getRows().size(); i++) {
            ExamExcelRow row = report.getRows().get(i);
            assertThat(row.hasErrors()).as("Hàng %d không được có lỗi", row.getRowNum()).isFalse();
            assertThat(row.getQuestionType()).isEqualTo("MULTIPLE_CHOICE");
            assertThat(row.getPromptJa()).isNotBlank();
            assertThat(row.getPromptVi()).isNotBlank();
            assertThat(row.getChoice1()).isNotBlank();
            assertThat(row.getChoice2()).isNotBlank();
            assertThat(row.getChoice3()).isNotBlank();
            assertThat(row.getChoice4()).isNotBlank();
            assertThat(row.getCorrectAnswer()).isIn("1", "2", "3", "4");
            assertThat(row.getExplanation()).isNotBlank();

            ParticleQuestionDef def = SAMPLE_QUESTIONS.get(i);
            coveredParticles.add(def.targetParticle());
        }

        // Đảm bảo đủ cả 9 loại trợ từ yêu cầu
        for (String p : requiredParticles) {
            assertThat(coveredParticles).as("Bộ câu hỏi phải bao gồm trợ từ " + p).contains(p);
        }
    }

    @Test
    @DisplayName("Kiểm tra quy trình xem trước và nộp (preview -> commit) tệp mẫu particles_50.xlsx")
    void testSampleFilePreviewAndCommitWorkflow() throws IOException {
        byte[] bytes = Files.readAllBytes(SAMPLE_FILE_PATH);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "particles_50.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes
        );

        // Bước 1: Preview
        ExamImportPreviewReport previewReport = examExcelService.preview(multipartFile, "127.0.0.1");
        assertThat(previewReport).isNotNull();
        assertThat(previewReport.isValid()).isTrue();
        assertThat(previewReport.canCommit()).isTrue();
        assertThat(previewReport.getToken()).isNotBlank();
        assertThat(previewReport.getQuestionCount()).isEqualTo(50);

        // Bước 2: Commit vào database (trong transaction test)
        ExamPaper paper = examExcelService.commit(previewReport.getToken(), "127.0.0.1");
        assertThat(paper).isNotNull();
        assertThat(paper.getId()).isNotNull();
        assertThat(paper.getCode()).isEqualTo("PARTICLES-A2-50");
        assertThat(paper.getTitle()).isEqualTo("50 Câu Luyện Tập Trợ Từ Tiếng Nhật Trình Độ A2");

        entityManager.flush();
        entityManager.clear();
        ExamPaper savedPaper = examService.getExamWithDetails(paper.getId());
        assertThat(savedPaper.getSections()).hasSize(1);
        assertThat(savedPaper.getTotalQuestions()).isEqualTo(50);
    }
}

