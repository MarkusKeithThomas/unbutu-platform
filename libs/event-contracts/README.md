# event-contracts

Định nghĩa schema event (Java record) dùng chung giữa producer và consumer: `BookingCreated`, `PaymentSucceeded`, ... Kèm `eventId`, `occurredAt`, `traceId` trong envelope.
Thay đổi phải **tương thích ngược** (chỉ thêm field optional).
