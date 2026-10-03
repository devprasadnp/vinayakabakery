# Sri Vinayaka Bakery Home — Spring Boot e-commerce store

A complete food shop: **veg and non-veg catalogue with your own photos**, cart, checkout,
JWT-secured REST API, a Thymeleaf storefront, an **admin panel**, a **super-admin panel**,
**delivery-area restriction by PIN code**, **delivery-partner logins**, **emailed password-reset links**,
and **PhonePe Payment Gateway (Standard Checkout v2)** — the gateway with no setup fee,
no annual fee and 0% MDR on UPI in India.

---

## Stack

| Layer      | Choice |
|------------|--------|
| Runtime    | Java 17, Spring Boot 3.3.5 — packaged as a **WAR** (external Tomcat 10.1+, or standalone) |
| Data       | PostgreSQL 16 + Spring Data JPA + Flyway migrations |
| Security   | Spring Security — JWT for `/api/**`, session + form login for the web pages |
| UI         | Thymeleaf + a hand-written green/red design system (no build step, no npm) |
| Photos     | Uploaded to a folder on disk, referenced from the database |
| Payments   | PhonePe PG Standard Checkout v2 (OAuth token → pay → status → webhook) |
| API docs   | springdoc-openapi → `/swagger-ui.html` |

---

## Run it

```bash
# PostgreSQL must exist first - the app never creates the database
psql -U postgres -h localhost -c "CREATE DATABASE cjgwzcmo_home;"

mvn spring-boot:run
```

Open <http://localhost:8080>. Mock payment mode is on by default, so no PhonePe
credentials are needed to walk the whole checkout.

| Role | Email | Password | Can do |
|------|-------|----------|--------|
| **Super admin** | `superadmin@srivinayakabakeryhome.com` | `Super@12345` | Everything below, plus accounts, archive and permanent delete |
| **Admin** | `admin@srivinayakabakeryhome.com` | `Admin@12345` | Catalogue, photos, orders |
| **Customer** | `customer@srivinayakabakeryhome.com` | `Customer@123` | Shop and track orders |
| **Delivery partner** | *none seeded* | created by an admin | See only the orders handed to them; mark them picked up / delivered |

The first start seeds **20 foods — 10 veg, 10 non-veg**.

---

## Build and deploy (WAR)

```bash
mvn clean package            # runs the tests, then builds target/sri-vinayaka-bakery-home-1.0.0.war
```

The WAR runs two ways:

| How | Command |
|---|---|
| **External Tomcat** | Copy the WAR into Tomcat's `webapps/`. Needs **Tomcat 10.1+** (Servlet 6 / Jakarta EE 10) and Java 17+ — Tomcat 9 and older will not work. |
| **Standalone** | `java -jar target/sri-vinayaka-bakery-home-1.0.0.war` (embedded Tomcat, as before) |

For an external Tomcat:

* **Context path = the WAR's file name.** Deploy it as `ROOT.war` to serve at the domain root (this is what
  `APP_BASE_URL=https://srivinayakabakeryhome.com` assumes). Any other name, e.g. `shop.war`, serves at `/shop`; every link,
  asset and redirect follows it automatically, but then set `APP_BASE_URL` and `PHONEPE_REDIRECT_URL` to include
  the path so emailed reset links and payment callbacks point at the right place.
* **Pick the profile.** The default is `dev` (SQL logging, template cache off). Set `SPRING_PROFILES_ACTIVE=prod`
  in the container's environment (or `-Dspring.profiles.active=prod` in `bin/setenv.sh|bat`).
* **Configuration comes from the environment.** The `.env` file is looked up relative to Tomcat's *working
  directory*, not the WAR, so under Tomcat use real environment variables or `-D` options for `SPRING_MAIL_PASSWORD`,
  `JWT_SECRET`, `DB_*`, etc.
* **Give uploads an absolute path**, e.g. `UPLOAD_DIR=/var/sri-vinayaka-bakery-home/uploads/products`. A relative path
  resolves against Tomcat's working directory, and a folder *inside* the exploded WAR is wiped on redeploy.
* Embedded Tomcat is a `provided` dependency, so it stays out of `WEB-INF/lib` and cannot clash with the
  container's own (`ServletInitializer` is the container entry point).

---

### Troubleshooting: the app deploys but cannot reach the database

The tell-tale line in the Tomcat log is

```
FATAL: no pg_hba.conf entry for host "127.0.0.1", user "…", database "…", no encryption
```

Everything else in that stack trace ("Unable to start web server", `entityManagerFactory`, Flyway…) is just the
cascade. It means PostgreSQL *answered* but its access rules (`pg_hba.conf`) have no entry letting that user reach
that database from that address — so it is fixed on the **database host**, not in the application:

* **cPanel:** *Remote PostgreSQL* (or *PostgreSQL Databases*) → allow the address named in the error (for example
  `127.0.0.1`, or the server's own IP), and confirm the user is added to the database with privileges.
* **Otherwise** ask the host to add `host  <database>  <user>  127.0.0.1/32  scram-sha-256` (`md5` on older
  servers) to `pg_hba.conf` and reload PostgreSQL.

**The app diagnoses this for you.** If it cannot reach the database at startup it re-tries the login on every route
from that server (the configured hosts, `127.0.0.1`, `[::1]` and the machine's own addresses), using its own
credentials, and prints what each one did:

```
    localhost:5432   refused by the server's access rules (pg_hba.conf) - no entry allows this user from here
    [::1]:5432       IPv6 is not available on this server
    203.0.113.7:5432 OK - this login works here
Action:  DB_HOSTS=203.0.113.7:5432   (environment variable, or -DDB_HOSTS=... in CATALINA_OPTS) and restart.
```

If one route works, the log names the exact `DB_HOSTS` value to set — no rebuild needed. (The driver itself only ever
reports the *last* host it tried, which is why a misleading error such as `Protocol family unavailable` can hide
the real refusal; the report above shows all of them.) The default is `localhost:5432` only — IPv6 (`[::1]`) is not
in the default because some servers have it switched off. If **no** route works, only the database host can fix it
(steps above). `DB_HOSTS` accepts a comma-separated list tried in order; set `DB_SSLMODE=require` if the host
insists on SSL, or `DB_URL` to replace the whole URL.

To see which way of connecting works from the server itself, run the diagnostic from the exploded WAR's `WEB-INF/lib`
(it uses the PostgreSQL driver already there; the password is read from an environment variable so it never appears in
the process list):

```bash
export DB_PASSWORD='the database password'
java -cp postgresql-42.7.4.jar /path/to/tools/DbCheck.java  <database>  <user>  [server-public-ip-or-hostname]
```

It tries `localhost`, `127.0.0.1`, `[::1]` and any extra host, with and without SSL, prints exactly what the server
said for each, and ends with what to do next. Whichever host shows `OK` can be set without rebuilding the WAR: put it in `DB_HOSTS` (or the whole URL in `DB_URL`),
as an environment variable or `-DDB_HOSTS=…` in `CATALINA_OPTS`.

The WAR itself has been verified on Tomcat 10.1.31 and 11.0.22.

---

## Who can do what

`USER`, `ADMIN` and `SUPER_ADMIN` are layered, not exclusive. A super admin also carries the admin and
customer authorities (`AppUserDetails.authoritiesFor`), so every `hasRole('ADMIN')` rule covers
them without a role-hierarchy bean. `DELIVERY_PARTNER` sits beside that ladder: it carries no admin
authority at all and reaches only `/delivery`.

| Capability | Customer | Delivery partner | Admin | Super admin |
|---|:--:|:--:|:--:|:--:|
| Browse, cart, checkout, track orders | ✓ | ✓ | ✓ | ✓ |
| See and update **their own assigned** deliveries | | ✓ | | |
| Add and edit foods, upload photos | | | ✓ | ✓ |
| Move orders along (packed, shipped, delivered) | | | ✓ | ✓ |
| **Choose which PIN codes we deliver to** | | | ✓ | ✓ |
| **Add delivery partners, edit / block them, hand orders to them** | | | ✓ | ✓ |
| **Reset a customer's or delivery partner's password** | | | ✓ | ✓ |
| Reset an **admin's** password, edit or block admins | | | | ✓ |
| Create customers, edit or block customers | | | | ✓ |
| Archive foods and orders, delete them permanently | | | | ✓ |

The "who may touch whom" rules live in `UserAdminService`, not just in the controllers, and are pinned by
`UserAdminServicePermissionTest`: an admin can never reset or edit another admin or a super admin,
because that would let them take over a more powerful account.

Roles themselves cannot be changed from the panel — promoting or demoting staff is
deliberately left to the database, so nobody can escalate their own access through the UI.

---

## Delivery areas (PIN code restriction)

Admin panel → **Delivery areas** (`/admin/delivery-areas`, admin and super admin).

* Keep a list of PIN codes you deliver to. Add one or many at once (commas, spaces or new lines), optionally
  labelled with an area name. **Pause** a PIN code without losing it, or **Remove** it.
* One switch — **"Only deliver to these PIN codes"** — turns the restriction on. **It is off by default**, so an
  existing shop keeps taking orders from everywhere until an admin opts in, and an empty list can never silently
  mean "deliver nowhere". (The switch refuses to turn on with no active PIN code.)
* While it is on, an address outside the list is refused in `OrderService.placeOrder` — **before stock is reserved
  and before the customer ever sees a payment screen** — with: *"Sorry, we don't have a delivery partner for PIN code
  560001 yet. Please use a different delivery address."* This is the single choke point, so the web checkout and
  `POST /api/orders` both obey it.
* **Orders already waiting for payment are covered too.** If you remove or pause a PIN code (or switch the restriction
  on) after a customer has placed an order but before they have paid, the rule is re-applied the moment they click
  *Scan QR to pay* (or retry payment, or call `POST /api/orders/{n}/pay`): the order is **cancelled**, its reserved stock
  goes back on the shelf, the customer is told why, and it can no longer be paid — the QR image is refused and an admin
  cannot mark it paid. (An order that is already `PAID` is never touched.)
* The checkout page also asks the server as soon as the six digits are typed and shows a green or red message,
  disabling the pay button for an unserved PIN code. That is only an early warning; the server check is authoritative.

## Delivery partners

Admin panel → **Delivery partners** (`/admin/delivery-partners`). Add as many as you need — each is its own account
with its own sign-in (name, email, mobile, password). Edit details, reset the password or block sign-in from **Manage**.

1. On **Orders**, pick a partner in the *Delivery partner* column for any paid, undelivered order and press **Assign**
   (choose *Unassigned* to take it back). Only enabled accounts with the delivery-partner role can be chosen.
2. The partner signs in at the normal sign-in page and lands on **My deliveries** (`/delivery`): customer name, tappable
   phone number, full address, what they are carrying. **Picked up — out for delivery** moves it to `SHIPPED`;
   **Mark delivered** moves it to `DELIVERED`.
3. A partner can only see and move orders assigned to *them*, and only along those two steps — they cannot cancel,
   refund, skip a step, browse other orders, or reach `/admin`.

## Passwords: forgot, reset, and admin reset

**Customers and staff — "Forgot your password?"** on the sign-in page emails a one-time link.

* The token is 256 bits of randomness; **only its SHA-256 is stored**. A link works once, expires after 60 minutes,
  and is dead as soon as a newer one is issued or the password changes.
* The form gives the same answer whether or not the address has an account (no account probing), skips blocked
  accounts, and will not send a second link to the same person within 60 seconds.
* Links are built from `APP_BASE_URL`, never from the request's `Host` header, so they cannot be poisoned.
* API: `POST /api/auth/forgot-password` (always `202`) and `POST /api/auth/reset-password`.

**Admins** can reset a customer's or delivery partner's password (set one, or generate one shown once) from
`/admin/customers` or `/admin/delivery-partners` → **Manage**. Super admins can reset anyone's.

### Turning on email

Without SMTP configured, dev mode writes the reset link to the application log so the flow still works locally.
Real email is switched on by the **`mail` profile** (automatic under `prod`):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev,mail
```

`application-mail.yml` holds the host, port, username and From address. **The mailbox password is never in a tracked
file** — put it in the git-ignored `.env` file next to `pom.xml`, or in a real environment variable:

```bash
SPRING_MAIL_PASSWORD=your-mailbox-password
```

Startup fails with a clear message if the `mail` profile is on and the password is missing.

> **TLS note.** The mailbox connects over *implicit* TLS - SMTPS port 465 and IMAPS port 993 - rather than
> plain SMTP/IMAP upgraded with STARTTLS, so the connection is encrypted from the first byte. See
> `application-mail.yml` for the exact properties (`mail.smtp.ssl.enable`, `spring.mail.imap.ssl`).

---

## Store location (Google Maps)

Admin panel → **Store location** (`/admin/store-location`, admin and super admin): set the
bakery's own latitude/longitude (drag a pin on the map, or type the numbers in by hand), an
address line, and a display name. It shows up on the storefront footer, and centres the optional
delivery-pin map a customer sees at checkout.

Everything here degrades gracefully with no `GOOGLE_MAPS_API_KEY` set: the admin screen falls
back to plain number fields (no map preview), the footer falls back to a plain "Open in Google
Maps" link, and checkout simply does not show a delivery-pin map at all (the address fields are
what delivery goes by regardless — the pin is purely a bonus for the delivery partner). Nothing
breaks either way; the map just lights up once a key is set. Create a key in
[Google Cloud Console](https://console.cloud.google.com/google/maps-apis) with the **Maps
JavaScript API** enabled and billing on, then restrict it to this site's domain(s):

```bash
GOOGLE_MAPS_API_KEY=your-key
```

The delivery partner dashboard and a customer's own order page both show a one-click
**Navigate / View dropped pin** link (plain `google.com/maps` URLs, no key needed) whenever a
pin was dropped at checkout.

## Product photos

Upload your own images straight from the product form. They are saved to disk and the
database keeps only the reference, so backups stay small and moving to S3 later is one class.

- **Where:** `app.storage.upload-dir`, default `./uploads/products` (relative to where you
  run the app). **Back this folder up with the database** — the two belong together.
- **Served at:** `/media/products/{filename}` by `MediaController`, with a 30-day cache header.
- **Accepted:** JPG, PNG, WEBP, GIF · up to 5 MB each · up to 6 per food (all configurable).
- **Safety:** the stored name is always a generated UUID, never anything the browser sent, so
  a crafted filename cannot escape the upload folder. Content type is checked against an allow-list.
- **Primary photo:** the first upload becomes the main one; change it with *Make main*.
  Listings, the cart and the admin tables all use the main photo, falling back to the optional
  external image URL, then to an illustrated placeholder.

Deleting a photo removes both the row and the file. Deleting a product for good removes all of its photos.

---

## Archive, then delete

Destructive actions take two deliberate steps, so a mis-click can never wipe order history.

1. **Archive** — the food or order disappears from every listing but stays in the database.
   Archiving an unpaid order cancels it and returns its stock, so inventory is never silently lost.
2. **Delete forever** — only available from *Archive & delete*, behind a confirm dialog.

A food referenced by any past order line **cannot** be deleted at all; the app tells you how
many orders hold it and asks you to leave it archived. Deleting an order removes its lines and
its payment records with it.

---

## Going live with PhonePe

1. Sign up at <https://business.phonepe.com> and complete KYC. No onboarding or annual fee;
   UPI settles at 0% MDR, cards and net banking carry the usual per-transaction rate.
2. From **Developer Settings → API keys**, copy `CLIENT_ID`, `CLIENT_SECRET`, `CLIENT_VERSION`.
3. Set the environment variables and flip mock mode off:

```bash
PHONEPE_MOCK=false
PHONEPE_ENVIRONMENT=SANDBOX          # then PRODUCTION after UAT sign-off
PHONEPE_CLIENT_ID=your-client-id
PHONEPE_CLIENT_SECRET=your-secret
PHONEPE_REDIRECT_URL=https://your-domain.in/payment/callback
PHONEPE_WEBHOOK_USERNAME=vinayakabakery
PHONEPE_WEBHOOK_PASSWORD=a-long-random-string
```

4. Register the webhook `https://your-domain.in/api/payments/phonepe/webhook` on the PhonePe
   dashboard with the **same** username and password. PhonePe authenticates each callback with
   `Authorization: SHA256(username:password)`, checked in constant time before anything is written.

| Step | Where | Endpoint |
|------|-------|----------|
| OAuth token (cached until 60s before expiry) | `PhonePeTokenManager` | `POST /v1/oauth/token` |
| Create the payment | `PhonePeGateway.initiate` | `POST /checkout/v2/pay` |
| Ask what happened | `PhonePeGateway.checkStatus` | `GET /checkout/v2/order/{merchantOrderId}/status` |
| Server-to-server callback | `PaymentWebhookController` | your `/api/payments/phonepe/webhook` |

Redirect and webhook both funnel into one **idempotent** `PaymentService.apply(...)`, so a
duplicate callback can never mark an order paid twice or release stock twice. Money is stored
as **whole paise in BIGINT** everywhere. `PaymentGateway` is a three-method interface, so adding
Razorpay or Cashfree is one more implementation.

## Going live with Paytm (Dynamic QR)

`payments.mode=PAYTM` generates a Paytm Dynamic QR for the exact order amount instead of the
store's static UPI-ID QR, and confirms the order automatically (order-status API + webhook) —
no admin has to type in a UTR. `payments.paytm.mock=true` (the default while `mode=PAYTM`) swaps
in a local mock checkout at `/mock-gateway/paytm/{orderNumber}` so the flow can be walked through
before a merchant account exists.

1. Sign up at <https://business.paytm.com> and complete KYC.
2. From the [API keys dashboard](https://dashboard.paytm.com/next/apikeys), copy the **MID** and
   **Merchant Key**, and note the **Website** and **Industry Type** shown there.
3. Set the environment variables, pick a `posId` (any short identifier for this storefront, e.g.
   `STORE1`), and flip mock mode off:

```bash
PAYMENTS_MODE=PAYTM
PAYTM_MOCK=false
PAYTM_ENVIRONMENT=STAGING            # then PRODUCTION after UAT sign-off
PAYTM_MID=your-mid
PAYTM_MERCHANT_KEY=your-merchant-key
PAYTM_WEBSITE=WEBSTAGING             # DEFAULT (or your assigned website) in production
PAYTM_INDUSTRY_TYPE=Retail
PAYTM_POS_ID=STORE1
PAYTM_CALLBACK_URL=https://your-domain.com/api/payments/paytm/webhook
```

4. Ask Paytm support to register the server-to-server callback URL above against your MID. Every
   posted form carries a `CHECKSUMHASH` field, verified with the official `paytm-checksum`
   library before anything is written — the same `merchant-key` signs outgoing requests too.

| Step | Where | Endpoint |
|------|-------|----------|
| Create the QR | `PaytmGateway.initiate` | `POST /paymentservices/qr/create` |
| Ask what happened | `PaytmGateway.checkStatus` | `POST /v3/order/status` |
| Server-to-server callback | `PaymentWebhookController` | your `/api/payments/paytm/webhook` |

The QR itself is just a `upi://pay` URI (Paytm's `qrData`), rendered with the same `QrCodeService`
the manual UPI-QR flow uses, stored on the `payments.qr_payload` column so `/orders/{orderNumber}
/qr-code.png` can re-render it on refresh without calling Paytm again.

> **Note.** Paytm's Create-QR and order-status wire format was reconstructed from Paytm's public
> docs and the official checksum library rather than live-tested against a sandbox account (this
> environment has no network access to Paytm). Run a real UAT order in `STAGING` before relying
> on it, and watch the application log — `PaytmGateway` logs the raw response on every error.

---

## Screens

| Area | Path |
|------|------|
| Storefront | `/`, `/products`, `/products/{slug}` |
| Cart & checkout | `/cart`, `/checkout` |
| My orders | `/orders`, `/orders/{orderNumber}` |
| Admin | `/admin`, `/admin/products`, `/admin/orders`, `/admin/delivery-areas`, `/admin/delivery-partners`, `/admin/customers` |
| Super admin | `/admin/staff`, `/admin/customers/new`, `/superadmin/archive` |
| Delivery partner | `/delivery` |
| Password | `/forgot-password`, `/reset-password?token=…` |
| Mock gateway (dev only) | `/mock-gateway/{merchantOrderId}` |

### Design

One palette, applied consistently: **green** is the brand and every safe action, **red** marks
non-veg, danger and anything destructive. That matches the veg/non-veg mark Indian shoppers
already read instinctively, so a customer never has to learn the interface. The admin and super
admin share a deep-green sidebar shell; destructive controls are the only red buttons in it.

Everything is defined as CSS custom properties at the top of `static/css/app.css` — change
`--green-600` and `--red-600` and the whole system follows.

---

## API

Full interactive docs at `/swagger-ui.html`.

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/auth/register`, `/api/auth/login` | — |
| POST | `/api/auth/forgot-password`, `/api/auth/reset-password` | — |
| GET | `/api/delivery-areas/check?pincode=533001` | — |
| GET | `/api/products?type=VEG&q=mango` | — |
| GET/POST/PUT/DELETE | `/api/cart`, `/api/cart/items/{productId}` | JWT |
| POST | `/api/orders`, `/api/orders/{orderNumber}/pay` | JWT |
| GET/POST/PUT/PATCH | `/api/admin/products`, `/api/admin/orders` | ADMIN |
| POST | `/api/admin/products/{id}/photos` (multipart) | ADMIN |
| GET/POST/PATCH/DELETE | `/api/admin/delivery-areas`, `/api/admin/delivery-areas/settings` | ADMIN |
| GET/POST | `/api/admin/delivery-partners`, `/api/admin/customers` | ADMIN |
| PUT/PATCH/POST | `/api/admin/accounts/{id}`, `…/enabled`, `…/password` | ADMIN (scope enforced per target) |
| PUT | `/api/admin/orders/{orderNumber}/delivery-partner` | ADMIN |
| GET/PATCH | `/api/delivery/orders`, `/api/delivery/orders/{orderNumber}/status` | DELIVERY_PARTNER |
| GET/POST/PUT/PATCH | `/api/superadmin/customers`, `/api/superadmin/accounts/{id}` | SUPER_ADMIN |
| POST/DELETE | `/api/superadmin/products/{id}/archive`, `/api/superadmin/products/{id}` | SUPER_ADMIN |
| POST | `/api/payments/phonepe/webhook` | SHA256 header |
| POST | `/api/payments/paytm/webhook` | CHECKSUMHASH field |

---

## Configuration

| Key | Default | Meaning |
|-----|---------|---------|
| `app.storage.upload-dir` | `./uploads/products` | Where photos are written |
| `app.storage.max-images-per-product` | 6 | Cap per food |
| `app.shipping.flat-fee-paise` | 6000 (₹60) | Flat courier fee |
| `app.shipping.free-above-paise` | 99900 (₹999) | Free shipping threshold |
| `JWT_SECRET` | dev placeholder | **Must** be ≥ 32 chars in production |
| `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` | superadmin@srivinayakabakeryhome.com | Seeded once |
| `PAYMENTS_MODE` | `QR` | `QR` (manual), `PHONEPE`, or `PAYTM` |
| `payments.phonepe.*` | see above | PhonePe gateway credentials |
| `payments.paytm.*` | see above | Paytm gateway credentials |
| `GOOGLE_MAPS_API_KEY` | — | Enables the map on checkout and in the footer/admin; blank falls back to a plain lat/lng field |
| `DB_HOSTS` | `localhost:5432` | Database hosts, tried in order |
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | see `application.yml` | Database name and login |
| `DB_SSLMODE` | `disable` | Set `require` if the database host insists on SSL |
| `DB_URL` | built from the above | Replaces the whole JDBC URL |
| `APP_BASE_URL` | `http://localhost:8080` | Public address; password-reset links are built from it |
| `SPRING_MAIL_PASSWORD` | — | Mailbox password, needed when the `mail` profile is on. Keep it in `.env` |
| `SPRING_MAIL_HOST` / `_PORT` / `_USERNAME` | see `application-mail.yml` | SMTP server settings |
| `app.password-reset.expiry-minutes` | 60 | How long a reset link lives |
| `app.password-reset.resend-cooldown-seconds` | 60 | Gap between two links for one account |

Flyway owns the schema: `V1__init.sql` creates it, `V2__images_archive_and_super_admin.sql`
adds photos, archive flags and the super-admin index, and `V5__delivery_areas_partners_password_reset.sql`
adds delivery PIN codes, the delivery-partner assignment on orders and password-reset tokens (purely additive). `spring.jpa.hibernate.ddl-auto` is
`none` by default and `validate` under the `prod` profile.

## Tests

```bash
mvn test
```

Money conversion, slug generation, PhonePe state mapping, webhook signature verification,
role layering, the delivery-zone rules, the admin/super-admin permission matrix, and end-to-end flows
(checkout refusal, delivery-partner journey, forgot/reset password) through the real Spring Security
chain against in-memory H2 — no PostgreSQL, no network. Flyway is off in tests, so migrations are verified by
starting the app against a real PostgreSQL.

## Before you take real money

- [ ] Change `JWT_SECRET`, both staff passwords and the webhook password.
- [ ] Serve over HTTPS; set `APP_BASE_URL` and `PHONEPE_REDIRECT_URL` to the live domain.
- [ ] Put `uploads/` on persistent storage and include it in your backups.
- [ ] Run the PhonePe UAT checklist in `SANDBOX`, then switch to `PRODUCTION`.
- [ ] Publish your FSSAI licence number, GSTIN and a refund policy.
- [ ] Set `SPRING_MAIL_PASSWORD` (and `APP_BASE_URL`) so password-reset emails go out — `prod` turns the `mail` profile on.
- [ ] Add your delivery PIN codes and switch the restriction on if you only serve certain areas.
- [ ] Add order confirmation email/SMS (the hook point is `PaymentService.apply`, and `MailService` is ready to reuse).
