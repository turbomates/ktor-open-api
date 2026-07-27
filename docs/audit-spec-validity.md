# Аудит: чего не хватает для генерации валидных OpenAPI-спек

Дата: 2026-07-25. Ветка: `claude/library-audit-spec-generation-jsvxuq`.

Цель аудита — не список желаемых фич (он есть в [`issues.md`](../issues.md)), а ответ на вопрос
**«почему генерируемая спека может быть невалидной или неверной по смыслу»**.

## Как проверялось

Каждая находка воспроизведена фактически: собирался `OpenAPI.root`, сериализовался тем же
`Json { encodeDefaults = false }`, что и в плагине, и прогонялся через `OpenAPIParser` из
`swagger-parser` — тот же валидатор, что используется в тестах. В отчёте приведены реальные
выхлопы и реальные сообщения валидатора.

Существующие тесты (`OpenAPITest`, `DescriptionBuilderTest`, `SwaggerPathTest`) проходят: они
покрывают только «счастливый путь» — плоский `data class` из `String`/`Double`/`Int` без
path-параметров. Практически всё за пределами этого набора либо ломает генерацию, либо даёт
невалидную спеку.

## Сводка

| # | Категория | Находка | Валидатор |
|---|---|---|---|
| A1 | ✅ исправлено | Path-параметры не документируются, если не использован overload с `TParams` | ошибка |
| A2 | ✅ исправлено | Nullable path-параметр даёт `required: false` | ошибка |
| A3 | ✅ исправлено | Query-параметры попадают в `in: path` из-за эвристики по `{` | 2 ошибки |
| A4 | ✅ исправлено | Шаблоны `{id?}`, `{path...}` уходят в спеку как есть, имя tailcard теряется | ошибка |
| A5 | ✅ исправлено | Пустой `paths` вообще не сериализуется | ошибка |
| A6 | ✅ исправлено | Повторная регистрация пути дублирует параметры | ошибка |
| A7 | невалидно | Модель `SecuritySchemaObject` эмитит недопустимые поля | 4 ошибки |
| A8 | невалидно | Поля без дефолтов сериализуются как явные `null` | — |
| A9 | игнорируется | `security` операции уходит под ключом `securitySchemaObject` | — |
| A10 | невалидно | `PathItemObject.servers`, `CallbackObject` описаны неверными типами | — |
| B1 | крэш | Рекурсивные типы → `StackOverflowError` | — |
| B2 | крэш | `List<T>`/`String` в ответе → `InvalidTypeForOpenApiType` | — |
| B3 | крэш | value class / enum в ответе → `ClassCastException` | — |
| B4 | крэш | Raw-генерики и `List<*>` → `NullPointerException` | — |
| B5 | ✅ исправлено | `HEAD`/`OPTIONS`/`TRACE` → `IllegalArgumentException` | — |
| C1 | неверно | Нет `required` — все поля схемы опциональны | — |
| C2 | неверно | `Int`/`Long` → `number`, `format` отсутствует в модели вовсе | — |
| C3 | неверно | `Map<K,V>` → `properties: {"String": ...}` вместо `additionalProperties` | — |
| C4 | неверно | `java.time.*`, `ByteArray` рефлексируются во внутренности | — |
| C5 | неверно | `sealed class` → пустой `{}`, без `oneOf`/`discriminator` | — |
| C6 | неверно | Вычисляемые getter'ы попадают в схему, порядок полей не декларационный | — |
| C7 | пробел | Нет `$ref`/`components` — схемы инлайнятся, `addModel` не вызывается | — |
| C8 | пробел | `description` ответов всегда `"empty description"` | — |
| C9 | пробел | `tags` принимаются в `get()` и молча выбрасываются | — |
| C10 | пробел | `info.title`/`version` нельзя изменить, `host` не используется | — |
| C11 | пробел | Только `application/json`, нет multipart/binary, нет заголовков | — |

---

## A. Спека получается невалидной

### A1. Path-параметры не документируются, если не использован overload с `TParams` — ✅ исправлено

Самый частый и самый болезненный случай. Валидность требует, чтобы каждая переменная шаблона пути
была описана параметром с `in: path`. Библиотека берёт параметры **только** из типа `TParams`,
поэтому любой из этих совершенно нормальных вариантов даёт невалидную спеку:

```kotlin
route("/users/{id}") {
    get<Res> { Res(true) }              // параметров нет вообще
}
get<Res>("/regex/{id}") { Res(true) }   // overload без TParams
```

Выхлоп и валидатор:

```json
"/users/{id}":{"get":{"responses":{...},"parameters":[]}}
```
```
paths.'/users/{id}'. Declared path parameter id needs to be defined as a path parameter in path or operation level
paths.'/regex/{id}'. Declared path parameter id needs to be defined as a path parameter in path or operation level
```

Причина: `Router.addToPath` (`Router.kt:12`) не знает про шаблон пути и не сверяется с ним.
Ничто не заставляет разработчика передать `TParams`.

**Минимально нужно**: парсить `{...}` из `route.buildFullPath()` и (а) автоматически добавлять
`in: path`, `required: true`, `schema: {type: string}` для не описанных переменных, либо
(б) падать с внятной ошибкой на старте приложения.

**Сделано** (вариант «а»): `OpenAPI.addToPath` (`OpenAPI.kt`) достаёт переменные шаблона через
`String.pathTemplateVariables()` (`PathTemplate.kt`) и те, которых нет в `pathParams`, описывает
как `{"in":"path","required":true,"schema":{"type":"string"}}` **на уровне path item** — там, где
им и место по смыслу: переменная принадлежит пути, а не отдельной операции. Операция может
переопределить такой параметр своим, спецификация это разрешает.

```json
"/users/{id}":{"get":{...},"parameters":[
  {"name":"id","in":"path","required":true,"schema":{"nullable":false,"type":"string"}}]}
```

Детали:

- имена нормализуются: `{id?}` и `{path...}` дают `id` и `path` (сам шаблон приводится к
  OpenAPI-виду в A4); переменная без имени пропускается — описывать её нечем;
- дубликатов нет: если переменная уже описана через `TParams`, повторно она не добавляется, в том
  числе при повторной регистрации того же пути;
- работает для всех глаголов и для вложенных `route`-блоков, так как правка живёт в ядре, а не в
  отдельных overload'ах.

Тесты: `PathParameterTest` — путь с параметром без `TParams` (и через `route {}`, и через
`get<T>(path)`), вложенные `route`-блоки с двумя переменными, отсутствие дублей при объявленном
`TParams` и при повторной регистрации. Каждый проверяет `OpenAPIParser(...).messages` на пустоту.

### A2. Nullable path-параметр даёт `required: false` — ✅ исправлено

`toParameterObject` (`OpenAPI.kt:107`) выводит `required` из nullability типа:
`required = it.type.isRequired`. Для `in: path` спецификация требует `required: true` **всегда**.

```kotlin
data class PathIdNullable(val id: UUID?)
```
```json
"parameters":[{"name":"id","in":"path","required":false,"schema":{"nullable":true,"type":"string"}}]
```
```
paths.For path parameter id the required value should be true
```

**Нужно**: для `INType.PATH` жёстко ставить `required = true`.

**Сделано**: `toParameterObject` (`OpenAPI.kt`) для `INType.PATH` ставит `required = true`
независимо от nullability свойства. Заодно у path-параметра снимается `nullable` в схеме:
`required: true` вместе с `nullable: true` — самопротиворечивое описание, а значение path-параметра
приходит куском URL и `null` быть не может. Query- и header-параметры не затронуты: там
nullability по-прежнему определяет `required`.

```json
"parameters":[{"name":"id","in":"path","required":true,"schema":{"nullable":false,"type":"string"}}]
```

Тесты: `PathParameterTest` — nullable path-параметр (`UUID?`) даёт `required: true` и
`nullable: false`; nullable query-параметр остаётся `required: false` / `nullable: true`
(регрессионная страховка на то, что правка не задела query).

### A3. Query-параметры классифицируются как path — ✅ исправлено

Разделение path/query сделано эвристикой «есть ли `{` в пути» (`Router.kt:34`, используется во всех
overload'ах вида `Get.kt:73-74`). Если путь шаблонный, а `TParams` описывает фильтр — все поля
уезжают в `in: path`:

```kotlin
data class Filter(val q: String?)
get<Res, Filter>("/mixed/{id}") { _ -> Res(true) }
```
```json
"parameters":[{"name":"q","in":"path","required":false,"schema":{"nullable":true,"type":"string"}}]
```
```
paths.For path parameter q the required value should be true
paths.'/mixed/{id}'. Declared path parameter id needs to be defined as a path parameter in path or operation level
```

Сразу две ошибки: несуществующий path-параметр `q` и недокументированный `id`.

**Нужно**: определять расположение по имени свойства — совпало с переменной шаблона → path,
иначе → query. Это заодно закрывает A1 и A3 одним механизмом.

**Сделано**: `classifyParameters` (`OpenAPI.kt`) складывает свойства `pathParams` и `queryParams` в
один список и раскладывает по имени: совпало с переменной шаблона → `in: path`, иначе → `in: query`.
Решение живёт в ядре, поэтому одинаково работает для всех глаголов и для прямых вызовов
`addToPath`. Одно имя описывается один раз, даже если упомянуто и в `TPath`, и в `TQuery` — иначе
получались бы дубли параметров.

```json
"/mixed/{id}":{"get":{"parameters":[
  {"name":"id","in":"path","required":true,"schema":{"nullable":false,"type":"string"}},
  {"name":"page","in":"query","required":true,"schema":{"nullable":false,"type":"number"}},
  {"name":"query","in":"query","required":false,"schema":{"nullable":true,"type":"string"}}]}}
```

Эвристика `String.containsPathParameters()` в overload'ах (`Get.kt:73-74` и аналоги) после этого
ни на что не влияет: в какой бы из двух слотов ни попал `TParams`, ядро всё равно разложит его по
именам. Функция оставлена как есть — вычистить её из overload'ов можно отдельным косметическим
проходом, поведение от этого не изменится.

Тесты: `PathParameterTest` — фильтр при шаблонном пути остаётся в query (плюс `id` документируется),
`TParams` с обоими видами параметров разъезжается правильно, явное разделение через
`get<TResponse, TQuery, TPath>` не сломано.

### A4. Optional-параметры и tailcard уходят в спеку как есть — ✅ исправлено

Ktor-синтаксис путей богаче OpenAPI. `buildFullPath()` (`Router.kt:38`) чистит только
`/(method:GET)`, всё остальное попадает в ключ `paths` буквально:

| Ktor | В спеке | Проблема |
|---|---|---|
| `/optional/{id?}` | `/optional/{id?}` | нет опциональных path-параметров в OpenAPI |
| `/files/{path...}` | `/files/{...}` | имя переменной потеряно, шаблон бессмысленный |

Оба дают `Declared path parameter ... needs to be defined`.

**Нужно**: нормализовать шаблон — `{id?}` → отдельный путь без параметра (или `{id}` +
`required: true`), tailcard → `{path}` с пометкой в описании.

**Сделано**: выяснилось, что Ktor 3.4 уже умеет это сам — есть `Route.path(RoutePathFormat)` и
готовый `OpenApiRoutePathFormat`. `buildFullPath()` (`Router.kt`) перешёл на них вместо разбора
`toString()` регуляркой. Выбран вариант «`{id}` + `required: true`» — так решает сам Ktor, и это же
поведение получается бесплатно вместе с остальной нормализацией.

| Ktor | Было | Стало |
|---|---|---|
| `/optional/{id?}` | `/optional/{id?}` | `/optional/{id}` |
| `/files/{path...}` | `/files/{...}` | `/files/{path}` |
| `/anonymous/{...}` | `/anonymous/{...}` | `/anonymous/{**}` |
| `/wildcard/*` | `/wildcard/*` | `/wildcard/{*}` |
| `/` | `` (пустой ключ!) | `/` |
| `/reports/(daily)/summary` | `/reports/summary` | `/reports/(daily)/summary` |

Имя tailcard'а `OpenApiRoutePathFormat` теряет (отдаёт `{**}`), но оно есть в
`PathSegmentTailcardRouteSelector.name`, поэтому поверх него навешен свой `RoutePathFormat` — он
подставляет настоящее имя, а на всё остальное делегирует в реализацию Ktor. Безымянный tailcard
остаётся `{**}`; параметры с именами `**` и `*` валидатор принимает.

Два бонуса от отказа от регулярки: пустой ключ `paths` для корневого роута (`/` вместо `""`) и
съеденные сегменты со скобками (`/reports/(daily)/summary` регулярка `/\(.*?\)/` вырезала целиком).
Замыкающий слэш срезается: Ktor матчит путь и с ним, и без него, а для OpenAPI `/x/` и `/x` — два
разных пути.

Не сделано из предложенного: пометка в описании для tailcard-параметра — ядро получает путь
строкой и не знает, какая переменная была tailcard'ом; прокидывать это ради косметики отдельно не
стал, вернуться стоит вместе с C8 (описания) или C11.

Тесты: `PathTemplateTest` — по одному на каждую строку таблицы выше плюс замыкающий слэш; все с
проверкой `OpenAPIParser(...).messages` на пустоту.

### A5. Пустой `paths` не сериализуется — ✅ исправлено

`Root.paths` (`Root.kt:9`) имеет дефолт `mutableMapOf()`, а плагин сериализует с
`encodeDefaults = false`. Приложение без задокументированных роутов отдаёт:

```json
{"openapi":"3.0.2","info":{"title":"Api","version":"0.1.0"}}
```
```
attribute paths is missing
```

`paths` — обязательное поле. **Нужно**: либо `@EncodeDefault` на `paths`, либо всегда
сериализовать непустой объект.

**Сделано**: `@EncodeDefault` на `Root.paths` (`Root.kt`). Приложение без задокументированных
роутов теперь отдаёт валидный документ:

```json
{"openapi":"3.0.2","info":{"title":"Api","version":"0.1.0"},"paths":{}}
```

Тест: `EmptyDocumentTest` — `install(OpenAPI)` без роутов, `messages` пусты, в выхлопе есть
`"paths":{}`.

### A6. Повторная регистрация пути дублирует параметры — ✅ исправлено

`merge` (`OpenAPI.kt:150`) конкатенирует списки параметров без дедупликации (теги, кстати,
дедуплицируются строкой ниже — `distinct()`):

```kotlin
repeat(2) { api.addToPath("/users", GET, ..., queryParams = typeOf<Pageable>().openApiKType.objectType()) }
```
```json
"parameters":[{"name":"page",...},{"name":"size",...},{"name":"page",...},{"name":"size",...}]
```
```
paths.'/users'(get).parameters. There are duplicate parameter values
```

Через Ktor-DSL это ловится, когда один и тот же путь+метод регистрируется дважды (разные
overload'ы, повторный вызов билдера роутов, монтирование модуля в двух местах).

**Нужно**: дедупликация по паре (`name`, `in`) при merge.

**Сделано**: `merge` (`OpenAPI.kt`) дедуплицирует параметры по паре (`name`, `in`) — повторная
регистрация пути и метода описывает те же параметры, а не новые. Побеждает описание, которое уже
лежит в операции. Пара, а не одно имя: `page` в path и `page` в query — два разных параметра, и
такое сочетание спецификация разрешает.

Отдельно стоит отметить сочетание с A1. Если один и тот же путь зарегистрирован и без `TParams`, и с
ним, получается так:

```json
"/users/{id}":{
  "get":{"parameters":[{"name":"id","in":"path","required":true,"schema":{"type":"number"}}]},
  "parameters":[{"name":"id","in":"path","required":true,"schema":{"type":"string"}}]}
```

В самой операции параметр один — объявленный, с настоящим типом. На уровне path item остаётся
сгенерированный fallback от той регистрации, которая тип не объявляла: он нужен операциям этого
пути, которые параметр не описали, а объявленное описание его переопределяет — спецификация это
разрешает, валидатор молчит. Это и есть причина, по которой A1 кладёт сгенерированные параметры на
path item, а не в операцию: два уровня сами по себе разделяют «объявлено» и «додумано», и порядок
регистраций перестаёт что-либо значить.

Тесты: `RepeatedRegistrationTest` — двойная регистрация пути с одинаковыми query-параметрами,
одноимённые path и query параметры, сочетание с A1 (в операции ровно один параметр и именно
объявленный), плюс прямой вызов `addToPath` дважды на уровне ядра.

### A7. Модель security-схем не соответствует спецификации

В `SecuritySchemaObject` (`Components.kt:91`) все поля, кроме `description` и `bearerFormat`, —
non-null и без дефолтов. Описать обычный bearer невозможно без выдуманных значений, и они уезжают
в JSON:

```json
"securitySchemes":{"BearerAuth":{"type":"http","description":null,"name":"Authorization",
 "in":"header","scheme":"bearer","bearerFormat":"JWT",
 "flows":{"authorizationUrl":"","tokenUrl":"","refreshUrl":"","scopes":{}},"openIdConnectUrl":""}}
```
```
attribute components.securitySchemes.BearerAuth.authorizationUrl is unexpected
attribute components.securitySchemes.BearerAuth.tokenUrl is unexpected
attribute components.securitySchemes.BearerAuth.refreshUrl is unexpected
attribute components.securitySchemes.BearerAuth.scopes is unexpected
```

Плюс `OAuthFlowsObject` (`Components.kt:103`) смоделирован как один набор полей, тогда как в
спецификации это map из четырёх именованных flow (`implicit`, `password`,
`clientCredentials`, `authorizationCode`).

**Нужно**: сделать все поля nullable с дефолтами `null` и завести отдельные flow-объекты.

### A8. Поля без дефолтов сериализуются как явные `null`

`encodeDefaults = false` не спасает поля, у которых дефолта нет вообще — в примере выше видно
`"description":null`. Затронуты: `SecuritySchemaObject.description`, `ExampleObject` (все поля),
`LinkObject` (все), `ExternalDocumentationObject.description`, `EncodingObject` (все),
`TagObject.description`/`externalDocs`, `ServerVariableObject`.

**Нужно**: `= null` дефолты на все опциональные поля spec-моделей.

### A9. `security` операции уходит под неправильным ключом

`OperationObject.securitySchemaObject` (`Components.kt:155`) сериализуется как
`"securitySchemaObject": {...}` — в спецификации поле называется `security`. Тулинг такое поле
просто игнорирует (Swagger UI не покажет замок, кодогенераторы не добавят авторизацию).

`Root.security` (`Root.kt:12`) — двойная проблема: это `val` (значение выставить нельзя) и тип
`List<SecuritySchemaObject>`, тогда как требуется список security requirements
(`List<Map<String, List<String>>>`).

**Нужно**: `@SerialName("security")`, правильный тип, `var`.

### A10. Ещё несоответствия spec-моделей

- `PathItemObject.servers: OperationObject?` (`Components.kt:139`) — должно быть `List<ServerObject>?`.
- `CallbackObject(pathObject: PathsObject)` + `PathsObject(path: PathItemObject)`
  (`Components.kt:121-124`) сериализуются в `{"pathObject":{"path":{...}}}`; по спецификации
  callback — это map «выражение → Path Item».
- `ServerVariableObject.enum: String` (`ServerObject.kt:13`) — должно быть `List<String>`.
- `OperationObject.server: ServerObject?` — в спецификации `servers: List<ServerObject>`.

Сейчас это не «стреляет» только потому, что перечисленные объекты нигде не заполняются.

---

## B. Генерация падает (спека вообще не собирается)

Все пять случаев — исключение при регистрации роута, то есть приложение падает на старте либо
`/openapi.json` отдаёт 500.

### B1. Рекурсивные типы → `StackOverflowError`

```kotlin
data class Node(val name: String, val children: List<Node>)   // StackOverflowError
data class MutualA(val b: MutualB)
data class MutualB(val a: MutualA)                            // StackOverflowError
```

Защита в `buildObjectType` (`OpenApiKType.kt:79`) — только `type != memberType`, то есть ловит
исключительно прямую ссылку на себя того же типа. `List<Node>` уже не ловится. Это следствие C7:
без `$ref` рекурсивную схему выразить нечем.

### B2. Коллекция или примитив в ответе → `InvalidTypeForOpenApiType`

```kotlin
get<List<User>>("/users") { ... }   // Invalid java.util.List<User> to build Object
get<String>("/ping") { "pong" }     // Invalid java.lang.String to build Object
```

`objectType()` (`OpenApiKType.kt:44`) требует, чтобы верхний уровень был объектом. Список в
ответе — абсолютно рядовой REST-кейс; сейчас его нужно оборачивать в wrapper-класс.

### B3. value class и enum в ответе → `ClassCastException`

`objectType()` делает безусловный `buildType(name, original) as Type.Object`
(`OpenApiKType.kt:48`), а `buildType` для value class разворачивает во вложенный тип
(`OpenApiKType.kt:68`), для enum — в `Type.String`:

```kotlin
@JvmInline value class Money(val amount: Long)
get<Money>("/price") { ... }   // Type$Number cannot be cast to Type$Object
get<Color>("/color") { ... }   // Type$String cannot be cast to Type$Object
```

### B4. Raw-генерики и star-projection → `NullPointerException`

`buildGenericTypes` (`OpenApiKType.kt:31`) делает `type.arguments[index].type!!`, а у
star-projection `type == null`:

```kotlin
typeOf<Generic<*>>().openApiKType.objectType()   // NPE
data class WithStar(val anything: List<*>)      // NPE
```

Аналогичные `!!` есть в ветке коллекций (`OpenApiKType.kt:93`) и map (`:110`, `:112`).

### B5. `HEAD`/`OPTIONS`/`TRACE` → `IllegalArgumentException` — ✅ исправлено

`Router.kt:24`: `com.turbomates.openapi.OpenAPI.Method.valueOf(method.value)`. В enum только
`GET, POST, PUT, DELETE, PATCH`:

```
IllegalArgumentException: No enum constant com.turbomates.openapi.OpenAPI.Method.HEAD
IllegalArgumentException: No enum constant com.turbomates.openapi.OpenAPI.Method.OPTIONS
```

Публичный `addToPath` принимает любой `HttpMethod`, так что уронить его легко. **Нужно**: добавить
`HEAD`/`OPTIONS`/`TRACE` в enum (`PathItemObject` их уже поддерживает) и не падать на неизвестном
методе.

**Сделано**: в `Method` добавлены `HEAD`, `OPTIONS`, `TRACE` и разложены по соответствующим полям
`PathItemObject`. `Router.addToPath` вместо `Method.valueOf(method.value)` ищет метод по имени без
учёта регистра и, если такого в OpenAPI нет, просто не документирует роут — `HttpMethod` открытый
тип, зарегистрировать роут можно на что угодно, а падать при регистрации из-за этого не за что.
Заодно перестал падать метод в нижнем регистре (`HttpMethod("delete")`).

Восьмикратный `when` по методам свёрнут: раньше это были пять почти одинаковых блоков по шесть
строк, теперь по строке на метод плюс общий `mergeOrCreate`. Тело запроса, как и раньше,
описывается только у методов, которые его несут; `HEAD`/`OPTIONS`/`TRACE` попали в ту же группу,
что `GET`.

DSL-функций `head`/`options`/`trace` по-прежнему нет — задокументировать такой роут можно через
публичный `addToPath`. Заводить под них overload'ы — это уже DSL, а не валидность (ближе к C11).

Тесты: `HttpMethodTest` — три метода документируются и валидируются, неизвестный метод (`LINK`)
не ломает регистрацию и не попадает в спеку, метод в нижнем регистре распознаётся,
`HEAD`/`OPTIONS` не получают `requestBody`.

---

## C. Спека валидна, но описывает API неверно

Это опаснее ошибок валидатора: спека проходит проверку и попадает клиентам, будучи неправдой.

### C1. Нет `required` — все поля опциональны

`SchemaObject` (`Components.kt:21`) вообще не имеет поля `required`. Обязательность выражается
только через `nullable`:

```kotlin
data class Simple(val id: UUID, val name: String, val age: Int, val nick: String?)
```
```json
{"nullable":false,"type":"object","properties":{
  "age":{"nullable":false,"type":"number"},
  "id":{"nullable":false,"type":"string"},
  "name":{"nullable":false,"type":"string"},
  "nick":{"nullable":true,"type":"string"}}}
```

Для любого кодогенератора и валидатора запросов **все четыре поля опциональны**. Это, пожалуй,
главная семантическая дыра: контракт запроса фактически не описан.

**Нужно**: добавить `required: List<String>?` в `SchemaObject` и заполнять его из non-null
свойств.

### C2. Числа и форматы

- `Int`, `Long`, `Short`, `BigDecimal` — всё `isSubtypeOf(Number)` → `"type":"number"`
  (`OpenApiKType.kt:24`). В OpenAPI есть `integer` c `int32`/`int64`.
- **`SchemaObject` не имеет поля `format` вообще.** Поэтому `UUID` — это просто `string`, а
  `date`/`date-time`/`email`/`binary` выразить нельзя даже через `customTypeDescription`:
  максимум, что можно получить, — безформатный `string`.
- Заодно в `SchemaObject` отсутствуют `title`, `description`, `default`, `oneOf`/`anyOf`/`allOf`,
  `minLength`/`maxLength`/`pattern`, `minimum`/`maximum`, `minItems`/`maxItems`/`uniqueItems`.

`Type` (`OpenAPI.kt:170`) тоже не имеет места для формата — правку нужно делать в обеих моделях.

### C3. `Map<K, V>` описывается неверно

`OpenApiKType.kt:109` строит объект с единственным свойством, названным по имени класса ключа:

```kotlin
data class WithMap(val meta: Map<String, Int>)
```
```json
"meta":{"nullable":false,"type":"object","properties":{"String":{"nullable":false,"type":"number"}}}
```

Спека утверждает, что у объекта есть поле с именем `String`. Правильно —
`{"type":"object","additionalProperties":{"type":"integer"}}`; поле `additionalProperties` в
`SchemaObject` уже есть (`Components.kt:32`) и нигде не используется.

### C4. `java.time.*` и `ByteArray` рефлексируются во внутренности

Ни один тип, не попавший в список примитивов (`OpenApiKType.kt:145`), не отбраковывается — он
молча раскладывается по member-property:

```kotlin
data class WithDate(val date: LocalDate)
// "date":{"type":"object","properties":{"year":{"type":"number"},"month":{"type":"number"},"day":{"type":"number"}}}

data class WithBytes(val payload: ByteArray)
// "payload":{"type":"object","properties":{"size":{"type":"number"}}}
```

Клиент по такой спеке сгенерирует структуру, которой в JSON никогда не будет (сериализатор отдаёт
`"2026-07-25"`). Через `customTypeDescription` лечится поштучно, но по умолчанию поведение —
тихий мусор вместо ошибки. Обратите внимание: `UnhandledTypeException` (`OpenApiKType.kt:27`)
недостижим — он выбрасывается из `primitiveType`, который вызывается только когда `isPrimitive()`
уже вернул `true`.

**Нужно**: из коробки маппить `java.time.*`/`kotlinx.datetime` в `string` + формат, `ByteArray` в
`string`/`binary`, а неизвестные типы без свойств — ловить явной ошибкой, а не разворачивать.

### C5. `sealed class` → пустой объект

```kotlin
sealed class Shape { data class Circle(val r: Double) : Shape(); data class Square(val a: Double) : Shape() }
get<Shape>(...)  // "schema":{"type":"object","properties":{}}
```

Ни `oneOf`, ни `discriminator` (`DiscriminatorObject` объявлен в `Components.kt:78` и не
используется). Полиморфные ответы описываются как пустой объект. То же для `Unit` —
`{"type":"object","properties":{}}` плюс `content` даже при 204.

### C6. Схема включает не то, что сериализуется

`buildObjectType` идёт по `memberProperties` (`OpenApiKType.kt:76`), поэтому:

- вычисляемые getter'ы попадают в схему: `val full: String get() = "$first $last"` → поле `full`;
- поля с `@Transient` попадают в схему, хотя в JSON их нет;
- порядок полей — не декларационный (`Simple(id, name, age, nick)` → `age, id, name, nick`);
- источник истины — рефлексия, а не `kotlinx.serialization`, поэтому `@SerialName` игнорируется:
  свойство `userId` с `@SerialName("user_id")` попадёт в спеку как `userId`.

Проверено на одном DTO — спека расходится с реальным JSON по двум полям из трёх:

```kotlin
@Serializable
data class Dto(
    @SerialName("user_id") val userId: String,
    @Transient val internal: String = "x",
    val kept: String
)
```
```
схема:      properties: internal, kept, userId
реальность: {"user_id":"1","kept":"k"}
```

То есть спека объявляет несуществующее поле `internal`, называет `user_id` как `userId` и не
описывает фактическое имя вовсе.

### C7. Нет `$ref` и `components`

`toSchemaObject` (`OpenAPI.kt:113`) всегда инлайнит схему целиком. `addModel` (`OpenAPI.kt:81`)
умеет писать в `components.schemas`, но из Ktor-слоя не вызывается никогда — `components` в
сгенерированной спеке всегда пуст. Следствия: рекурсивные типы невозможны (B1), общий DTO в
20 эндпоинтах дублируется 20 раз, спека распухает, в Swagger UI нет раздела Schemas.

### C8. Описания ответов — заглушка

`toResponseObject` (`OpenAPI.kt:92`) хардкодит `"empty description"` — эта строка попадает в
каждый ответ каждого эндпоинта.

### C9. `tags` принимаются и молча выбрасываются

`Get.kt:19` объявляет `tags: List<String> = emptyList()` — и не передаёт их дальше. Ядро
(`OpenAPI.addToPath`, `OpenAPI.kt:29`) теги поддерживает и даже корректно мёржит, но
`Router.addToPath` (`Router.kt:12`) параметра `tags` не имеет. Проверено: теги в спеку не
попадают. У остальных глаголов (`post`/`put`/`patch`/`delete`) параметра `tags` нет вовсе.

Это единственная находка, где путь исправления — одна строка в `Router.kt` плюс параметр в
overload'ах.

### C10. Метаданные документа не настраиваются

- `root.info` захардкожен: `InfoObject("Api", version = "0.1.0")` (`OpenAPI.kt:19`). Поле `info` —
  `val` в `data class Root`, а сам `root` — `val` в `OpenAPI`, так что **изменить title/version
  из `configure` невозможно**.
- `OpenAPI(var host: String)` (`OpenAPI.kt:18`) нигде не используется. README учит писать
  `SwaggerOpenAPI("api.example.com")`, но `servers` в спеку не попадает — значение просто лежит в
  поле.
- `root.tags` — `val` (`Root.kt:13`), описания тегов задать нельзя.

### C11. Content-type, заголовки, коды ответов

- `application/json` захардкожен и в ответах, и в теле запроса (`OpenAPI.kt:95`, `:101`). Нет
  multipart/form-data, нет `application/octet-stream`, нет нескольких media type.
- `INType.HEADER` объявлен (`OpenAPI.kt:167`) — DSL для header-параметров нет. Cookie-параметров
  нет вовсе.
- `responseCodeMap` отображает один тип ответа в набор кодов, поэтому у всех кодов одна и та же
  схема; описать «200 → User, 404 → Error» для конкретного роута нельзя.
- Нет `operationId` — кодогенераторы будут выдумывать имена методов.
- Ключи `responses` — `Map<Int, ...>`, зарезервированный `default` невыразим.

---

## Предлагаемый порядок работ

### Этап 1 — убрать невалидность (без этого спека не годится для тулинга)

1. **Path-параметры из шаблона пути** — парсить `{...}` из `buildFullPath()`, добавлять
   недостающие, `required: true` всегда, размещение определять по имени, а не по наличию `{`
   в пути. Закрывает A1, A2, A3. — ✅ сделано.
2. **Нормализация шаблонов** `{id?}` / `{param...}` (A4). — ✅ сделано.
3. **`paths` всегда в выхлопе** (A5) — одна аннотация. — ✅ сделано.
4. **Дедупликация параметров при merge** (A6). — ✅ сделано.
5. **`HEAD`/`OPTIONS`/`TRACE` в `Method`** и отсутствие падения на неизвестном методе (B5). — ✅ сделано.

### Этап 2 — убрать крэши

6. **`components` + `$ref`** с реестром уже построенных схем — одновременно закрывает B1
   (рекурсия) и C7 (дублирование). Ключевая правка архитектуры.
7. **Верхний уровень ответа любого типа** — `toSchemaObject` вместо `objectType()`: массивы,
   примитивы, enum, value class (B2, B3).
8. **Убрать `!!`** в `buildGenericTypes`/коллекциях/map, star-projection → пустая схема (B4).

### Этап 3 — привести смысл в соответствие

9. **`required` в `SchemaObject`** из non-null свойств (C1) — самая важная семантическая правка.
10. **`format` в `SchemaObject` и в `Type`**, `integer` для целых, встроенные маппинги
    `java.time.*`/`UUID`/`ByteArray` (C2, C4).
11. **`Map` → `additionalProperties`** (C3).
12. **Источник истины — `kotlinx.serialization`** (`SerialDescriptor`) вместо `memberProperties`:
    сразу закрывает `@SerialName`, `@Transient`, порядок полей, дефолты и обязательность (C6, и
    сильно упрощает C1). Самая крупная, но и самая окупаемая переделка.
13. **`sealed class` → `oneOf` + `discriminator`** (C5).

### Этап 4 — метаданные и DSL

14. `info`/`servers` настраиваемые, `host` использовать по назначению (C10).
15. Прокинуть `tags` (C9) — тривиально, ядро готово.
16. Описания ответов, `operationId`, `summary` (C8).
17. Починить spec-модели security/callbacks/servers и дефолты `null` (A7, A8, A9, A10).
18. Header-параметры, content-type, per-route коды ответов (C11).

### Тестовое покрытие

Текущие тесты пропускают всё вышеперечисленное. Минимальный набор, который стоит завести вместе
с правками: путь с параметром без `TParams`; nullable path-параметр; рекурсивный тип; `List<T>` в
ответе; enum и value class в ответе; `Map`; `LocalDate`; `sealed class`; `@SerialName`; повторная
регистрация пути; пустое приложение. Каждый — с проверкой `OpenAPIParser().readContents(...).messages`
на пустоту и с проверкой смысла (наличие `required`, `format`, `additionalProperties`).

---

## Что уже работает корректно

Чтобы картина была честной: плоские DTO из `String`/`Number`/`Boolean`/`UUID`, nullable-поля,
enum (включая nullable), вложенные объекты, коллекции объектов и примитивов, дженерики с
явным аргументом (включая подстановку проекций), value class как **свойство**, слияние
операций по одному пути и разным методам, `customTypeDescription` для объектных типов,
раздача `/openapi.json` и Swagger UI через webjars — всё это генерируется и проходит валидатор.
