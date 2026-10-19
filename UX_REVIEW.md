# Outgo — Đánh giá workflow / UI / UX

- **Ngày:** 2026-10-19
- **Phiên bản:** 4.8.0 (debug build, `app.outgo.debug`)
- **Cách test:** chạy trên emulator `mdn_test` (1080×2400), đi qua cả 7 tab, form chuyển khoản, sửa giao dịch, dark mode; đối chiếu lại với code.

Mức độ: 🔴 lỗi đã tái hiện · 🟠 mâu thuẫn workflow · 🟡 mâu thuẫn UI/hiển thị · 🔵 Analysis · ⚪ thiếu tính năng

## 🔴 Lỗi (đã tái hiện được)

1. **Giờ mặc định ở màn Add bị "đóng băng".** Giờ chỉ được lấy lúc mở màn hình và sau mỗi lần lưu. Lúc test, đồng hồ máy là 18:58 mà form vẫn ghi 18:54. Nếu app chạy nền vài tiếng hay qua ngày hôm sau, giao dịch sẽ bị lưu sai giờ hoặc sai ngày. Nguyên nhân: `occurredAt` trong `TradeViewModel.kt:33`; khi quay lại tab, code chỉ gọi `refreshPickers()`, không cập nhật lại thời gian.
2. **Tab Categories luôn mở ra khi đã cuộn xuống giữa danh sách** (sau khi mở lại app). Mấy mục đầu như Ăn uống, Di chuyển bị khuất, phải tự kéo lên.
3. **Phím ✓ trên bàn phím số ở ô nhập tiền không làm gì cả.** `AmountField` không có `ImeAction`/`keyboardActions`, nên bàn phím nằm đó che nút SAVE; phải bấm Back rồi mới bấm Save được.

## 🟠 Mâu thuẫn trong workflow

4. **Có 3 kiểu lưu khác nhau:**
   - Sửa giao dịch: phải bấm Save, có hỏi trước khi bỏ thay đổi.
   - Sửa tài khoản / danh mục: tự lưu, nhưng nút ‹ có nhãn "Cancel" và bên dưới vẫn có nút "Close". Người dùng tưởng là huỷ, thực ra đã lưu rồi.
   - Tạo mới: phải bấm Save.

   Nên chọn một kiểu, hoặc ít nhất đổi nhãn cho đúng.
5. **Không nhập được số lẻ, trong khi tiền tệ mặc định là `$` và có cho chọn 20 loại tiền.** `Money.parseDigits` chỉ lấy chữ số, nên "4.50" thành 450. Hoặc hỗ trợ phần thập phân, hoặc chỉ cho chọn tiền không có số lẻ (VND, JPY…) và để VND làm mặc định.
6. **Luồng nhập nhanh vẫn còn chậm** (dù đây là yêu cầu quan trọng nhất — mở app là vào thẳng Add):
   - Ô tiền không tự focus khi mở.
   - Vùng bấm của ô tiền rất hẹp: bấm vào ký hiệu `$` không ăn.
   - Khi bàn phím hiện thì che mất nút Save.
   - Sau khi lưu, ngày bị đặt lại về hôm nay, nên nhập bù nhiều khoản của hôm qua phải chọn lại ngày từng lần.
7. **Hai hàng "Recent" và "Most used" trùng nhau.** Trong 10 ô, Cà phê và Ăn ngoài xuất hiện 2 lần, chọn thì cả hai ô cùng sáng. Nên lọc trùng để có thêm lựa chọn khác.
8. **"Undo" sau khi lưu chỉ xoá giao dịch, không trả dữ liệu vào form.** Bấm Undo vì nhập sai một chỗ thì phải nhập lại từ đầu.
9. **Màn sửa giao dịch trông y hệt màn thêm mới:**
   - Không có tiêu đề.
   - Nút Save nằm dưới cùng, sau Cancel và Delete, phải cuộn mới thấy.
   - Không đổi được loại Chi ↔ Thu.
10. **Bấm vào tài khoản hay danh mục là vào thẳng form sửa.** Không có màn xem lịch sử giao dịch của riêng tài khoản / danh mục đó.
11. **History tách riêng Expense / Income / Transfer.** Không xem được tất cả giao dịch theo thời gian. Bộ lọc chỉ có Account và Category: không có khoảng ngày, không tìm theo ghi chú (ví dụ "Highlands").

## 🟡 Mâu thuẫn trong UI / hiển thị

12. **Màu đỏ mang hai nghĩa.** Thanh ngân sách lấy màu danh mục, nên "Ăn uống: 4,094,000 remain" hiện chữ đỏ dù còn nhiều, trông giống "Mua sắm: 80,000 over".
13. **Phần trăm trên thẻ ngân sách không có nhãn** (là so với tháng trước) và tô màu ngược ý: "Mua sắm" đang vượt ngân sách vẫn hiện "-58%" màu xanh.
14. **Home có 5 con số số dư ngang hàng** (Free / Budgets / Savings / Available / Total). Thứ bậc bị ngược: Free to nhất, Total nhỏ nhất. Home không ghi đang xem tháng nào.
15. **Dấu `>` dùng cho hai việc.** Ở Accounts / Categories, `>` cạnh số tiền là mở danh sách con, không phải "vào chi tiết"; `>` nhỏ dưới thanh tiến độ lại mở biểu đồ.
16. **Định dạng số không thống nhất:**
   - Ô tiền ghi `45,000$`, chỗ khác ghi `45,000 $`.
   - Ngân sách ghi `3,406,000 / 7,500,000`, tiết kiệm ghi `800,000/2,000,000`.
   - Ô số dư / hạn mức trong form sửa ghi `7500000`, không có dấu phân cách.
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

1, 3, 2 (lỗi rõ, sửa nhanh) → 4, 5, 6 (ảnh hưởng trực tiếp luồng nhập chi tiêu) → phần còn lại.

## Trạng thái

| # | Trạng thái |
|---|------------|
| 1 | ✅ đã sửa — giờ theo đồng hồ (làm mới mỗi 30 giây và khi quay lại màn hình) cho tới khi người dùng tự chọn ngày/giờ; lúc lưu dùng giờ hiện tại nếu chưa chọn. `TradeViewModel.refreshTime()`, `TradeScreen.kt` |
| 2 | ✅ đã sửa — nút "+" cuối danh sách đổi key khi danh sách đang rỗng, nên không còn bị "neo" xuống cuối khi dữ liệu tải xong (áp dụng cho cả Accounts). `TreeList.kt` |
| 3 | ✅ đã sửa — ô số tiền có `ImeAction.Done`, bấm ✓ thì ẩn bàn phím. `AmountField.kt` |
| 4–25 | chưa làm |
