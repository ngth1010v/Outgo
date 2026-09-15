hãy đóng vai 1 system design để thiết kế cho tối 1 app android nhẹ + nhanh phục vụ việc quản lí chi tiêu (tập trung vào phần chi tiêu hơn là thu).
app nên có:
- dùng duy nhất 1 file .sqlite (hoặc .ext tương đương) để lưu toàn bộ user data (phục vụ việc backup)
- tốc độ mở app phải thật nhanh
- khi vừa mở app sẽ nhảy ngay vào phần "thêm chi tiêu" thay vì home
có hệ thống hỗ trợ:
- nhiều 'tài khoản' để chứa tiền  
- nhiều 'danh mục', gồm danh mục cha và danh mục con có thể thêm/chỉnh sửa
- nhiều Budget để giới hạn việc chi của 1 số danh mục
- hỗ trợ Budget tiết kiệm để đặt mục tiêu
- hỗ trợ nhiều icon để gán cho các danh mục / tài khoản (icon là png và hỗ trợ import runtime)
- hỗ trợ lịch sử giao dịch lớn (mỗi trade có thể là tăng hoặc giảm, cả tăng và giảm đều có danh sách riêng)
- app sẽ có vài page chính:
    - home: gồm:
        - tổng tài khoảng hiện tại 
        - danh sách các Budget dưới dạng row (còn bao nhiêu, bao gồm cả Budget tiết kiệm)
        - vertical bar chart chồng nhau của chi/thu 5 tháng gần nhất bao gồm tháng hiện tại (mỗi cột = 1 tháng, trong cột đó có nhiều phần là cho từng danh mục, các danh mục thu sẽ xếp chồng nhau phía trên baseline và các danh mục chi thì ngược lại (dưới baseline), chỉ thống kế trên danh mục cha)
    - Trade (là giao diện để thêm trade): gồm:
        - 2 ratio button được xếp thành hàng ngang là thu và chi
        - số tiền:
            - có font đậm và tô hơn 1 chút, được center, không nhận số âm, có màu xanh lá khi thu và đỏ khi chi
        - chọn danh mục con: gồm 2 row 5 column + icon + tên danh mục con ở dưới mỗi icon:
            - row 1: cho 5 danh mục con gần nhất (nếu chưa có thì random)
            - row 2: cho 5 danh mục con có số lượng trade dùng nhiều nhất
        - lấy / thêm vào tài khoản nào
        - ngày giờ thêm: được chia thành 2 cột:
            - column1: ngày với format: ngày/tháng/năm
            - column2: thời gian với format: hour/minute
        - chú thích: 1 dòng cho chú thích ngắn
        - cuối trang: nút lưu (sau khi ấn lưu thì vần giữ ở trang trade, chỉ lưu sau đó clean các option bên trên)
    - Balance: trang gồm các row:
        - mỗi row là 1 balance, gồm 2 column được space between:
            - trái: gồm icon, tên
            - phải: số dư (được tô đậm)
        - row cuối là row new:
            - chỉ gồm kí tự '+' ở giữa
        - *note: mỗi row (bao gồm cả new button), khi click vào sẽ mở popup để edit (hoặc create nếu là new button) để chỉnh:
            - balance name
            - số dư hiện tại
            - icon
            - delete button
    - Category: trang gồm các row:
        - mỗi row là 1 danh mục cha, gồm phần row của cha và phần list cho các row con bên dưới:
            - phần row cha:
                - icon (sát bên trái + padding)
                - tên (sát icon + padding)
                - arrow icon (xếp/mở child list, sát phải + padding)
            - phần row con:
                - mỗi row là 1 danh mục con:
                    - icon (sát bên trái + padding)
                    - tên (sát icon + padding)
                - row cuối là row new:
                    - chỉ gồm kí tự '+' ở giữa
            - *note: khi danh mục có gán budget, phía dưới row chính sẽ có thêm 1 process bar (cả danh mục cha và con đều có thể có budget), phần thêm được chia thành 2 row:
                - trên: chia làm 2 column được space between:
                    - trái: số tiền còn lại (text color có màu từ xanh lá -> vàng -> đỏ với rule: số tiền đã chi > 90% tổng budget -> vàng, số tiền đã chi > tổng budget -> đỏ, còn lại xanh lá)
                    - phải: "số tiền đã chi" / "tổng budget"
                - dưới: thanh process:
                    - chỉ "số tiền đã chi" / "tổng budget", có màu là màu text của số tiền còn lại ở trên
        - row cuối là row new:
            - chỉ gồm kí tự '+' ở giữa
        - *note: mỗi row (bao gồm cả new button), khi click vào sẽ mở popup để edit (hoặc create nếu là new button) để chỉnh:
            - tên danh mục
            - icon
            - set budget: tổng tiền được dùng trên tháng
            - delete button
    - Analysis:
        - tạm thời để trống

    - Setting:
        - các setting khác 

- app có 1 navigation ở dưới cùng để route đến các page chính, bạn có thể tải các icon cho navigation từ `https://phosphoricons.com/`

*NOTE:
- hãy xuất ra 1 file achitecture.md + dùng chủ yếu là chart marmaid để minh họa các flow (có thể dùng các dạng khác, không ép tất cả về mermaid)

