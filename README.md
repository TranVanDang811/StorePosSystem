# 🛒 POS System – Retail Management

A modern Point of Sale (POS) system designed to streamline retail operations, manage orders, and track business performance efficiently.

 ### 🚀 Overview

This project is a full-stack POS (Point of Sale) system built to support daily operations in convenience stores or small retail businesses.

#### It helps manage:

🧾 Orders & payments

👥 Customers

📦 Products & inventory

👨‍💼 Staff shifts (open/close shift)

📊 Revenue & reports

## 🖼️ Demo Screenshots

### 🔐 Authentication
<img width="1919" height="978" alt="image" src="https://github.com/user-attachments/assets/f4b5c686-1491-4c6a-b0a1-44d6b71495d5" />

### 🛒 POS Interface
<img width="1919" height="980" alt="image" src="https://github.com/user-attachments/assets/b7c93eb0-fc64-4717-973b-9055e4ea97f8" />

### Payment Interface
<img width="1919" height="980" alt="image" src="https://github.com/user-attachments/assets/480ee0be-c750-456e-9529-a91c3ef78af6" />

### Settlement
<img width="1919" height="978" alt="image" src="https://github.com/user-attachments/assets/115bb1d8-31e1-417c-891c-95b1dc2588a1" />

### 📦 Product Management
<img width="1919" height="980" alt="image" src="https://github.com/user-attachments/assets/9eb76726-2c32-4fdc-97d2-083ef5d8c84e" />

### 📊 Reports Dashboard
<img width="1896" height="978" alt="image" src="https://github.com/user-attachments/assets/d2845843-ae17-49ff-8483-accb681fd418" />

### Import Product
<img width="1919" height="978" alt="image" src="https://github.com/user-attachments/assets/7b35ea8b-ee71-4f45-9cd4-0ecd838837c7" />

## 🏗️ Architecture

Backend: Spring Boot (REST API)

Frontend: React + TypeScript + Redux Toolkit

Database: MySQL / PostgreSQL

Authentication: JWT + Refresh Token

Cloud Storage: Cloudinary (for product images)

## ⚙️ Features

### 🛒 Order Management

Create orders with multiple products

Auto calculate total price

Track order status (PENDING, COMPLETED, CANCELLED)

### 💳 Payment

Support multiple payment methods

Track payment status

### 📦 Product Management

CRUD products

Upload & manage images (Cloudinary)

Stock tracking

### 👥 User Management

Role-based access (Admin / Staff)

Update user info via modal

Enable/disable users

### 👨‍💼 Shift Management

Open / Close shift

Cash counting system

Shift report export (PDF)

### 📊 Reports & Analytics

Revenue statistics

Top-selling products

Filter by date/time

### 🧠 Business Logic Highlights

Prevent selling when no active shift

Auto-close or warn overdue shifts

Token auto-refresh using Axios Interceptor

Clean DTO mapping with MapStruct

# 🚀 Live Demo

🌐 Frontend: [https://github.com/TranVanDang811/Interface-Frontend-Pos.git](https://github.com/TranVanDang811/Interface-Frontend-Pos.git)

🔗 Backend API: [https://github.com/TranVanDang811/StorePosSystem.git](https://github.com/TranVanDang811/StorePosSystem.git)


