INSERT INTO roles (name, description) VALUES
    ('Developer', 'Văn phong kỹ thuật, súc tích, đi thẳng vào vấn đề. Ưu tiên thuật ngữ chính xác, câu ngắn gọn, tránh vòng vo.'),
    ('Tech Lead', 'Văn phong tự tin, thể hiện rõ trách nhiệm và định hướng kỹ thuật. Nêu rõ đánh giá rủi ro, trade-off khi trình bày.'),
    ('BA', 'Văn phong rõ ràng, tập trung vào yêu cầu nghiệp vụ và làm rõ phạm vi. Tránh thuật ngữ kỹ thuật quá sâu, ưu tiên để người không chuyên hiểu được.'),
    ('PM', 'Văn phong chuyên nghiệp, hướng kết quả và tiến độ. Nhấn mạnh mốc thời gian, rủi ro, và hành động tiếp theo.'),
    ('QA/Tester', 'Văn phong chi tiết, chính xác, mô tả rõ các bước tái hiện và kết quả mong đợi/thực tế.');

INSERT INTO contexts (name, description) VALUES
    ('Meeting online', 'Giao tiếp dạng nói, trang trọng vừa phải, câu ngắn, dễ nói thành lời, phù hợp trao đổi trực tiếp qua video call.'),
    ('Clarify requirement', 'Trang trọng, rõ ràng, đặt câu hỏi mạch lạc, tránh mơ hồ, giúp người đọc hiểu đúng ý cần làm rõ.'),
    ('Comment Jira', 'Ngắn gọn, súc tích, đi thẳng vào vấn đề, phù hợp ghi chú trong ticket/issue tracker.'),
    ('Team chat', 'Thân thiện, tự nhiên, ít trang trọng, phù hợp trao đổi nhanh qua Slack/Teams với đồng nghiệp.'),
    ('Email/Report', 'Trang trọng, cấu trúc rõ ràng, đầy đủ ngữ cảnh, phù hợp văn bản chính thức gửi qua email hoặc báo cáo.');
