# Outgo — Đánh giá workflow / UI / UX

- **Ngày:** 2026-10-19
- **Phiên bản:** 4.8.0 (debug build, `app.outgo.debug`)
- **Cách test:** chạy trên emulator `mdn_test` (1080×2400), đi qua cả 7 tab, form chuyển khoản, sửa giao dịch, dark mode; đối chiếu lại với code.

Mức độ: 🔴 lỗi đã tái hiện · 🟠 mâu thuẫn workflow · 🟡 mâu thuẫn UI/hiển thị · 🔵 Analysis · ⚪ thiếu tính năng

## 🟠 Mâu thuẫn trong workflow

Mục 1–8, 10, 11 đã xử lý (xem bảng Trạng thái cuối file). Còn lại:

9. **Màn sửa giao dịch** (đã có tiêu đề "Sửa giao dịch"):
   - Nút Save nằm dưới cùng, sau Cancel và Delete, phải cuộn mới thấy.
   - Không đổi được loại Chi ↔ Thu.

## 🟡 Mâu thuẫn trong UI / hiển thị

12. **Màu đỏ mang hai nghĩa.** Thanh ngân sách lấy màu danh mục, nên "Ăn uống: 4,094,000 remain" hiện chữ đỏ dù còn nhiều, trông giống "Mua sắm: 80,000 over".
13. **Phần trăm trên thẻ ngân sách không có nhãn** (là so với tháng trước) và tô màu ngược ý: "Mua sắm" đang vượt ngân sách vẫn hiện "-58%" màu xanh.
14. **Home có 5 con số số dư ngang hàng** (Free / Budgets / Savings / Available / Total). Thứ bậc bị ngược: Free to nhất, Total nhỏ nhất. Home không ghi đang xem tháng nào.
15. **Dấu `>` dùng cho hai việc.** Ở Accounts / Categories, `>` cạnh số tiền là mở danh sách con, không phải "vào chi tiết"; `>` nhỏ dưới thanh tiến độ lại mở biểu đồ.
16. **Định dạng số không thống nhất:**
   - Ô tiền ghi `45,000$`, chỗ khác ghi `45,000 $`.
   - Ngân sách ghi `3,406,000 / 7,500,000`, tiết kiệm ghi `800,000/2,000,000`.
17. **Tiêu đề màn hình không đồng nhất.** Accounts, Categories, History, Settings có tiêu đề; Home, Add, Analysis thì không. Thanh điều hướng 7 tab chỉ có icon, không có chữ.
18. **Thẻ tiết kiệm có hai con số hướng dẫn cạnh nhau:** "92,307 $/day" và "425,806 $ need to save today" — không rõ nên làm theo số nào.
19. **Vài chỗ lệch nhỏ:**
   - Hộp chọn ngày / giờ là kiểu cũ, màu xanh ngọc, lệch với theme M3; lịch cho chọn ngày tương lai.
   - Ô "To account" khi chưa chọn hiện icon ảnh hỏng kèm "—".
   - "Xe buýt / Taxi" bị cắt thành "Xe buýt /".
   - Thông báo lỗi hiện ra làm cả form bị đẩy xuống.

## 🔵 Analysis

20. **Một trang cuộn có khoảng 18 biểu đồ, không có mục lục hay tab.** Phần tài khoản nằm trên cùng, phần Expense ("tiền đi đâu") ở giữa. Nên có tóm tắt Thu / Chi / Còn lại ở đầu, cộng tab Accounts / Expense / Income.
21. **Danh mục con dùng chung màu với danh mục cha**: biểu đồ của "Ăn uống" có 3 phần cùng màu đỏ, không phân biệt được.
22. **Chú thích (legend) bị cắt**: biểu đồ 8 đường nhưng chỉ hiện 5, cụt chữ "Sức…". Danh sách cạnh biểu đồ tròn cuộn riêng bên trong trang đã cuộn, dễ cuộn nhầm.
23. **Nút "Now" khi vô hiệu nhạt gần như không thấy**; thanh năm và thanh tháng xếp chồng chiếm nhiều chỗ.

## ⚪ Thiếu tính năng

24. **Không có dark mode.** Theme chỉ có `dynamicLightColorScheme`; bật night mode thì app vẫn sáng (CLAUDE.md lại nhắc đến việc lưu theme).
25. **Settings chưa có:** chọn theme, xuất CSV, nhắc nhập chi tiêu hằng ngày.

## Thứ tự đề xuất

Còn lại: 9 → 12–19 (UI) → 20–23 (Analysis) → 24–25.

## Trạng thái

| # | Trạng thái |
|---|------------|
| 1 | ✅ đã sửa — giờ theo đồng hồ (làm mới mỗi 30 giây và khi quay lại màn hình) cho tới khi người dùng tự chọn ngày/giờ; lúc lưu dùng giờ hiện tại nếu chưa chọn. `TradeViewModel.refreshTime()`, `TradeScreen.kt` |
| 2 | ✅ đã sửa — nút "+" cuối danh sách đổi key khi danh sách đang rỗng, nên không còn bị "neo" xuống cuối khi dữ liệu tải xong (áp dụng cho cả Accounts). `TreeList.kt` |
| 3 | ✅ đã sửa — ô số tiền có `ImeAction.Done`, bấm ✓ thì ẩn bàn phím. `AmountField.kt` |
| 4 | ✅ đã sửa — sửa tài khoản / danh mục không tự lưu nữa: có nút Save, Cancel và nút ‹ là huỷ thật (có thay đổi thì hỏi trước). Thay đổi ở nhiều tháng (ngân sách / mục tiêu tiết kiệm) được giữ khi chuyển tháng và lưu cùng lúc. Cả app chỉ còn một kiểu lưu. `EditorScaffold.kt`, `BalanceScreen.kt`, `CategoryScreen.kt` (bỏ `AutoSave.kt`) |
| 5 | ✅ đã sửa — nhập được 2 số lẻ. Tiền lưu theo đơn vị 1/100 (migration 20→21 nhân mọi số tiền cũ ×100); số lẻ chỉ hiện khi khác 0 (`45,000` vẫn là `45,000`, `4.5` hiện `4.50`). Settings có thêm "Định dạng số": `1,234.56` hoặc `1.234,56`. Gõ `.` hay `,` đều được hiểu là dấu thập phân. `Money.kt`, `MoneyTextField.kt`, `OutgoDatabase.kt` |
| 6 | ✅ đã sửa — bấm chỗ nào trên hàng số tiền (cả ký hiệu `$`) cũng focus; mở tab Add là ô tiền tự focus và hiện bàn phím; sau khi lưu chỉ xoá số tiền và ghi chú, giữ loại / danh mục / tài khoản / ngày giờ đã chọn (tắt hẳn app mở lại thì form trống). `AmountField.kt`, `TradeScreen.kt`, `TradeViewModel.resetForm()` |
| 7 | ✅ đã sửa — danh mục đã có ở hàng Recent thì không lặp lại ở hàng Most used (hàng dưới lấy thêm mục khác cho đủ 5). `CategoryRepository.getPicker()` |
| 8 | ✅ đã sửa — Undo sau khi tạo giao dịch xoá giao dịch và trả toàn bộ dữ liệu (số tiền, ghi chú, danh mục, tài khoản, ngày giờ…) về form để sửa rồi lưu lại. `TradeViewModel.undo()` |
| 9 | 🟡 một phần — đã thêm tiêu đề "Sửa giao dịch"; hai ý còn lại giữ nguyên theo yêu cầu |
| 10 | — giữ nguyên (đã chấp nhận cách hiện tại) |
| 11 | ✅ đã sửa — tab History là một danh sách chung Chi / Thu / Chuyển khoản theo thời gian (ngày có nhiều loại thì tổng là thu − chi). Bộ lọc có thêm: loại giao dịch, từ ngày / đến ngày, ghi chú có chứa, khoảng số tiền. `HistoryScreen.kt`, `HistoryFilterUi.kt`, `TradeDao.historyPage` |
| 12–25 | chưa làm |

Ghi chú khi test lần này:
- Khi ô tiền tự focus, bàn phím che thanh điều hướng dưới: muốn chuyển tab thì bấm ✓ hoặc Back để ẩn bàn phím trước.
- Đã sửa thêm một lỗi gặp khi làm mục 5: khôi phục backup tạo từ bản 4.8.0 (schema 20) bị báo "cần bản mới hơn" vì `SCHEMA_VERSION` vẫn là 19. Giờ hằng số này là version của Room luôn.
