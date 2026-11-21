// Schema event dùng chung giữa producer và consumer: chỉ Java record, KHÔNG phụ thuộc Spring/Kafka/Jackson
// (service nào cũng dùng được, đổi broker không phải sửa ở đây).
plugins {
    id("ticket.java-conventions")
    `java-library`
}
