# HR Management System

Company HR and internship management built with Java 17, Spring Boot, Thymeleaf, Spring Security, and Cloud Firestore.

## Features

- Separate dashboards and access rules for Admin, HR, Manager, Employee, and Intern.
- Staff account creation, role-based IDs, department assignment, and employee profiles.
- Intern registration, university and major details, and department-specific Senior mentors.
- Unique email and phone checks, with one Manager per department.
- Server-timed daily check-in/check-out and private check-in photo uploads.
- Manager attendance confirmation by date, followed by HR review lists and department filters.
- Audited supplementary check-ins and time corrections for Admin/HR.
- Missing check-out lists for previous days, scoped to each user's permissions.
- Leave request approval/rejection by Admin and HR.

## Local Setup

1. Install JDK 17 and clone this repository.
2. Obtain the Firebase service-account JSON from the project owner through a private channel. Do not commit it or post it in GitHub issues.
3. Place it at `src/main/resources/firebase-service-account.json`. The file is excluded from Git.
4. Run from the repository root:

```powershell
.\mvnw.cmd spring-boot:run
```

On Windows, the standalone launcher is also available:

```powershell
java -Xms16m -Xmx64m StartWeb.java
```

Open http://localhost:8080/login. Stop any existing application instance before restarting after code changes.

The launcher expects Windows and a working Java installation. Maven dependencies require network access on the first run.

## Firebase and Attendance

The application uses `users`, `employees`, `interns`, `attendance`, and `leave_requests`. It creates documents when records are saved; no manual collection creation is required.

Check-in photos are validated as JPEG/PNG, compressed to JPEG, and stored separately in `attendance_photos`. Originals must be no larger than 5 MB. Compressed photos are limited to 200 KB and served through authenticated application routes. Attendance time corrections keep audit entries in each attendance document's `corrections` subcollection.

New check-ins wait for a photo, then enter the Manager's queue. A Manager submits records for their own department and selected date. HR sees submitted records. Audited manual entries made by Admin/HR are also available to HR. Existing historical records are preserved.

Attendance uses the configured company timezone, defaulting to `Asia/Ho_Chi_Minh`. A check-in from a previous day without a check-out appears in **Attendance > Missing Check-out**. Today's open records are not automatically marked missing.

Do not use a shared production Firebase database for automated experiments. Team members using the same Firebase project share its data. Arrange access and test data with the project owner.

## Tests

```powershell
.\mvnw.cmd test
```

The suite includes authentication and staff rules, transactional attendance operations, image validation, leave approvals, permission checks, and template rendering. The application-context test requires valid Firebase configuration. Tests are not a substitute for independent security or load testing.

## Security Notes

- Never commit Firebase keys, exported user data, passwords, or production configuration secrets.
- Bootstrap Admin credentials currently come from constants in `AuthService`. Replace the default bootstrap password and configure credentials securely before deployment. Do not expose a deployment that still uses the default account password.
- New-account password fields intentionally display the entered value. Login and password-change fields remain masked. Stored passwords are hashed, not stored as plain text.
- Photos support manual attendance review; they do not independently verify capture location or date.
- Define photo retention, backups, and production access policies before using real employee data.
