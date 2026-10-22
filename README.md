<div dir="rtl">

# JWT – פרויקט הגשה

מימוש של REST API מאובטח: התחברות עם שם משתמש וסיסמה, קבלת **JWT**, וגישה לנתיבים שונים לפי **תפקיד** (משתמש רגיל / מנהל).

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql)](https://www.postgresql.org/)
[![JWT](https://img.shields.io/badge/JWT-jjwt%200.13.0-black)](https://github.com/jwtk/jjwt)

**מאגר:** [github.com/NoamBehor/JWT](https://github.com/NoamBehor/JWT)

---

## סטאק

| רכיב | גרסה / הערה |
|------|-------------|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring Security + JWT (jjwt) | 0.13.0 |
| מסד נתונים | PostgreSQL 16 |
| כלים נוספים | JPA, Lombok, Validation |

---

## ארכיטקטורה – מבט על

הלקוח מדבר רק עם Spring MVC. אימות מתבצע בפילטר לפני שהבקשה מגיעה ל-controller; נתוני משתמשים ותפקידים נשמרים ב-PostgreSQL.

</div>

```mermaid
flowchart LR
    subgraph client [Client]
        HTTP[HTTP Client / Postman / IntelliJ]
    end
    subgraph app [Spring Boot App]
        MVC[Controllers]
        Filter[JwtAuthFilter]
        Sec[SecurityConfig]
        Svc[Services]
        JPA[Spring Data JPA]
    end
    subgraph data [Data]
        PG[(PostgreSQL)]
    end
    HTTP --> MVC
    MVC --> Svc
    Filter --> Sec
    Filter --> Svc
    Svc --> JPA
    JPA --> PG
    HTTP -.->|Bearer JWT| Filter
```

<div dir="rtl">

---

## מפת אזורי אבטחה

| אזור | Prefix | טוקן | תפקיד מינימלי |
|------|--------|------|----------------|
| ציבורי | `/api/auth/**`, `/api/public/**` | לא נדרש | — |
| משתמש | `/api/user/**` | כן | `ROLE_USER` |
| מנהל | `/api/admin/**` | כן | `ROLE_ADMIN` |

</div>

```mermaid
flowchart TB
    subgraph zones [Security zones]
        Public["/api/public + /api/auth"]
        UserZone["/api/user/**"]
        AdminZone["/api/admin/**"]
    end
    Public -->|permitAll| OK200[200 OK]
    UserZone -->|hasRole USER| OK200
    AdminZone -->|hasRole ADMIN| OK200
    UserZone -->|wrong role| E403[403 Forbidden]
    AdminZone -->|wrong role| E403
    UserZone -->|no valid token| E401[401 Unauthorized]
    AdminZone -->|no valid token| E401
```

<div dir="rtl">

---

## מה ה-API מציע

**אימות (ציבורי)**  
- `POST /api/auth/login` – מחזיר טוקן `Bearer`  
- `POST /api/auth/register` – רישום; תמיד מתקבל `ROLE_USER` בלבד (201)

**דוגמאות לנתיבים מוגנים**  
- `GET /api/public/hello` – ללא טוקן  
- `GET /api/user/me` – דורש `ROLE_USER`  
- `GET /api/admin/users` – דורש `ROLE_ADMIN`

| קוד | משמעות בפרויקט |
|-----|----------------|
| 200 | הצלחה |
| 201 | משתמש חדש נרשם |
| 400 | שדות לא תקינים (`@Valid`) |
| 401 | חסר/שגוי/פג תוקף JWT |
| 403 | מזוהה אך ללא תפקיד מתאים |
| 409 | שם משתמש תפוס |

---

## תרשים רצף: התחברות (Login)

</div>

```mermaid
sequenceDiagram
    participant C as Client
    participant API as UserLoginController
    participant S as UserLoginService
    participant AM as AuthenticationManager
    participant DB as PostgreSQL
    participant J as JwtUtil

    C->>API: POST /api/auth/login
    API->>S: login(username, password)
    S->>AM: authenticate
    AM->>DB: load user + BCrypt check
    DB-->>AM: UserDetails + roles
    AM-->>S: authenticated
    S->>J: buildToken(user)
    J-->>S: signed JWT
    S-->>API: LoginResponse
    API-->>C: 200 + Bearer token
```

<div dir="rtl">

---

## תרשים רצף: בקשה מוגנת

</div>

```mermaid
sequenceDiagram
    participant C as Client
    participant F as JwtAuthFilter
    participant J as JwtUtil
    participant DB as PostgreSQL
    participant SC as SecurityContext
    participant R as ResourceController

    C->>F: GET /api/user/me + Authorization Bearer
    F->>J: parse + verify signature
    J-->>F: claims valid
    F->>DB: load fresh user + roles
    DB-->>F: enabled user
    F->>SC: set Authentication
    F->>R: continue filter chain
    R-->>C: 200 UserResponse JSON
```

<div dir="rtl">

---

## עץ החלטות: האם הבקשה מאושרת?

מבוסס על `JwtAuthFilter` + `SecurityConfig` (stateless, בלי session).

</div>

```mermaid
flowchart TD
    Start[Incoming request] --> HasBearer{Authorization starts with Bearer?}
    HasBearer -->|No| Anonymous[Anonymous]
    HasBearer -->|Yes| ValidJwt{Signature OK and not expired?}
    ValidJwt -->|No| Anonymous
    ValidJwt -->|Yes| UserOk{User exists and enabled in DB?}
    UserOk -->|No| Anonymous
    UserOk -->|Yes| Authenticated[Authenticated in SecurityContext]
    Anonymous --> PublicPath{URL is permitAll?}
    PublicPath -->|Yes| Allow[200 - controller]
    PublicPath -->|No| Deny401[401 Unauthorized]
    Authenticated --> RoleOk{Role matches URL rule?}
    RoleOk -->|Yes| Allow
    RoleOk -->|No| Deny403[403 Forbidden]
```

<div dir="rtl">

---

## מבנה טוקן JWT (HS256)

הטוקן **חתום** אבל **לא מוצפן** – כל מי שמחזיק בו יכול לקרוא את ה-payload; לכן לא שמים בו סיסמאות או מידע רגיש.

</div>

```mermaid
flowchart LR
    subgraph jwt [JWT string]
        H[Header.alg typ]
        P[Payload.sub roles exp]
        S[Signature HMAC]
    end
    H --> P --> S
```

<div dir="rtl">

---

## מודל נתונים

יחס **רבים-לרבים** בין משתמשים לתפקידים (`user_roles`). סיסמה בטבלה `users` – רק hash BCrypt.

</div>

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : has
    ROLES ||--o{ USER_ROLES : assigned
    USERS {
        bigint id PK
        varchar username UK
        varchar password "BCrypt"
        boolean enabled
    }
    ROLES {
        bigint id PK
        varchar name UK
    }
    USER_ROLES {
        bigint user_id FK
        bigint role_id FK
    }
```

<div dir="rtl">

---

## זרימה בקצרה (טקסט)

1. **Login** – הסיסמה נבדקת מול BCrypt ב-DB; בהצלחה `JwtUtil` מנפיק טוקן חתום (HS256).  
2. **בקשה עם טוקן** – `JwtAuthFilter` מאמת חתימה ותוקף, טוען את המשתמש **מחדש** מה-DB (כולל תפקידים), וממלא `SecurityContext`.  
3. **הרשאות** – `SecurityConfig` קובע לפי prefix של URL.

השרת **stateless**: אין session; הטוקן נשלח בכותרת `Authorization: Bearer …`.

---

## מבנה הפרויקט (תמצית)

</div>

```mermaid
flowchart TB
    subgraph web [Web layer]
        ULC[UserLoginController]
        RC[ResourceController]
    end
    subgraph domain [Domain]
        ULS[UserLoginService]
        US[UserService]
    end
    subgraph infra [Infrastructure]
        SEC[security package]
        CFG[config package]
        REPO[repositories]
    end
    ULC --> ULS
    RC --> US
    ULS --> REPO
    US --> REPO
    SEC --> REPO
    CFG --> SEC
```

<div dir="rtl">

```
src/main/java/com/example/jwtlesson/
  config/       SecurityConfig, DataInitializer
  controller/   auth + דוגמאות ל-public/user/admin
  dto/          בקשות/תשובות (records, בלי סיסמה בפלט)
  entity/       User, Role, RoleName
  repository/   UserRepository, RoleRepository
  security/     JwtUtil, JwtAuthFilter, CustomUserDetailsService
  service/      login, register, רשימת משתמשים
  exception/    טיפול ב-401/409/400 כ-JSON
```

בתיקיית `http/` – בדיקות IntelliJ HTTP Client (19 תרחישים).  
בתיקיית `scripts/` – `security-tests.ps1` לאותן בדיקות מול שרת חי.

---

## הרצה מקומית

### 1. PostgreSQL

```bash
docker compose up -d
```

חיבור: `localhost:5432`, DB `jwt_lesson`, משתמש/סיסמה `postgres` (ניתן לדרוס עם `DB_USER` / `DB_PASSWORD`).

### 2. האפליקציה

IntelliJ: `JwtLessonApplication` · או `mvn spring-boot:run` · פורט **8080**.

### 3. משתמשים לדוגמה

| שם משתמש | סיסמה | תפקידים |
|----------|--------|---------|
| admin | Admin123! | ADMIN + USER |
| user | User123! | USER |

### 4. בדיקות

| אופן | פקודה / מיקום |
|------|----------------|
| IntelliJ | `http/security-tests.http`, סביבה `dev`, Run All |
| PowerShell | `.\scripts\security-tests.ps1` |
| Maven | `mvn test` (H2 בזיכרון, 19 תרחישים) |

---

## נקודות אבטחה שיושמו

| נושא | יישום בפרויקט |
|------|----------------|
| אחסון סיסמה | BCrypt בלבד |
| הרשאות | Roles ב-DB, לא מהלקוח ב-register |
| Enumeration | אותה הודעת 401 ל-login שגוי |
| Session | Stateless + JWT בכותרת |
| תפקידים מעודכנים | טעינת user מה-DB בכל בקשה בפילטר |
| 401 מול 403 | `authenticationEntryPoint` + `accessDeniedHandler` מפורשים |

---

## מקור והשראה

הפרויקט מבוסס על תרגיל Spring Security + JWT (מבנה דומה לשיעור jwt-lesson), עם הרחבות: `docker-compose`, בדיקות Maven, סקריפט PowerShell ותיעוד עם תרשימים להגשה.

</div>
