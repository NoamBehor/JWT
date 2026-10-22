<div dir="rtl">

# JWT – פרויקט הגשה

מימוש של REST API מאובטח: התחברות עם שם משתמש וסיסמה, קבלת **JWT**, וגישה לנתיבים שונים לפי **תפקיד** (משתמש רגיל / מנהל).

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

## מה ה-API מציע

**אימות (ציבורי)**  
- `POST /api/auth/login` – מחזיר טוקן `Bearer`  
- `POST /api/auth/register` – רישום; תמיד מתקבל `ROLE_USER` בלבד (201)

**דוגמאות לנתיבים מוגנים**  
- `GET /api/public/hello` – ללא טוקן  
- `GET /api/user/me` – דורש `ROLE_USER`  
- `GET /api/admin/users` – דורש `ROLE_ADMIN`

**קודי תגובה שחשוב לזכור**  
- `401` – אין טוקן, טוקן לא תקין, או שפג תוקף  
- `403` – מזוהה, אבל התפקיד לא מתאים לנתיב  

---

## זרימה בקצרה

1. **Login** – הסיסמה נבדקת מול BCrypt ב-DB; בהצלחה `JwtUtil` מנפיק טוקן חתום (HS256).  
2. **בקשה עם טוקן** – `JwtAuthFilter` מאמת חתימה ותוקף, טוען את המשתמש **מחדש** מה-DB (כולל תפקידים), וממלא `SecurityContext`.  
3. **הרשאות** – `SecurityConfig` קובע לפי prefix של URL (`/api/admin`, `/api/user`, וכו').

השרת **stateless**: אין session; הטוקן נשלח בכותרת `Authorization: Bearer …`.

---

## מבנה הפרויקט (תמצית)

```
src/main/java/com/example/jwtlesson/
  config/       SecurityConfig, DataInitializer
  controller/   auth + דוגמאות ל-public/user/admin
  dto/          בקשות/תשובות (records, בלי סיסמה בפלט)
  entity/       User, Role, RoleName (many-to-many → user_roles)
  repository/   UserRepository (+ EntityGraph ל-roles), RoleRepository
  security/     JwtUtil, JwtAuthFilter, CustomUserDetailsService
  service/      login, register, רשימת משתמשים
  exception/    טיפול ב-401/409/400 כ-JSON
```

בתיקיית `http/` יש קובץ בדיקות ל-IntelliJ HTTP Client.  
בתיקיית `scripts/` יש סקריפט PowerShell לאותן בדיקות מול שרת רץ.

---

## הרצה מקומית

### 1. PostgreSQL

מהשורש של הפרויקט:

```bash
docker compose up -d
```

(חיבור: `localhost:5432`, DB `jwt_lesson`, משתמש/סיסמה `postgres` – ניתן לשנות ב-`application.properties` או במשתני סביבה.)

### 2. האפליקציה

הרצה מ-IntelliJ: `JwtLessonApplication`, או:

```bash
mvn spring-boot:run
```

פורט ברירת מחדל: **8080**.

### 3. משתמשים לדוגמה (נוצרים בהעלאה)

| שם משתמש | סיסמה | תפקידים |
|----------|--------|---------|
| admin | Admin123! | ADMIN + USER |
| user | User123! | USER |

### 4. איך לבדוק

| אופן | פקודה / מיקום |
|------|----------------|
| IntelliJ | `http/security-tests.http`, סביבה `dev`, Run All |
| PowerShell | `.\scripts\security-tests.ps1` (השרת חייב לרוץ) |
| Maven (בלי Docker) | `mvn test` – H2 בזיכרון, 19 תרחישים |

---

## הערות אבטחה (למידה)

- סיסמאות ב-DB רק כ-hash (BCrypt).  
- ב-Register אין שדה `roles` – לא ניתן “לבחור” ADMIN מהלקוח.  
- הודעת login זהה לסיסמה שגויה ולמשתמש לא קיים (מניעת enumeration).  
- תוכן JWT **לא מוצפן** – רק חתום; לא לשים בו סודות.  
- בפרודקשן: `JWT_SECRET`, `DB_PASSWORD` וכו' כמשתני סביבה, לא בקובץ properties.

---

## מקור והשראה

הפרויקט מבוסס על תרגיל Spring Security + JWT (מבנה דומה לשיעור jwt-lesson), עם התאמות להרצה, בדיקות ותיעוד אישי להגשה.

</div>
