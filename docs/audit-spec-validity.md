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
| B1 | ✅ исправлено | Рекурсивные типы → `StackOverflowError` | — |
| B2 | ✅ исправлено | `List<T>`/`String` в ответе → `InvalidTypeForOpenApiType` | — |
| B3 | ✅ исправлено | value class / enum в ответе → `ClassCastException` | — |
| B4 | ✅ исправлено | Raw-генерики и `List<*>` → `NullPointerException` | — |
| B5 | ✅ исправлено | `HEAD`/`OPTIONS`/`TRACE` → `IllegalArgumentException` | — |
| C1 | ✅ исправлено | Нет `required` — все поля схемы опциональны | — |
| C2 | ✅ исправлено | `Int`/`Long` → `number`, `format` отсутствует в модели вовсе | — |
| C3 | ✅ исправлено | `Map<K,V>` → `properties: {"String": ...}` вместо `additionalProperties` | — |
| C4 | ✅ исправлено | `java.time.*`, `ByteArray` рефлексируются во внутренности | — |
| C5 | ✅ исправлено | `sealed class` → пустой `{}`, без `oneOf`/`discriminator` | — |
| C6 | ✅ исправлено | Вычисляемые getter'ы попадают в схему, порядок полей не декларационный | — |
| C7 | ✅ исправлено | Нет `$ref`/`components` — схемы инлайнятся, `addModel` не вызывается | — |
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

### B1. Рекурсивные типы → `StackOverflowError` — ✅ исправлено

```kotlin
data class Node(val name: String, val children: List<Node>)   // StackOverflowError
data class MutualA(val b: MutualB)
data class MutualB(val a: MutualA)                            // StackOverflowError
```

Защита в `buildObjectType` (`OpenApiKType.kt:79`) — только `type != memberType`, то есть ловит
исключительно прямую ссылку на себя того же типа. `List<Node>` уже не ловится. Это следствие C7:
без `$ref` рекурсивную схему выразить нечем.

**Сделано** вместе с C7 — описание см. там. Коротко: `OpenApiKType` держит множество типов, которые
описываются прямо сейчас; повторная встреча типа из этого множества даёт `Type.Ref` — ссылку на
схему, частью которой этот тип и является, — вместо бесконечного спуска. Ловятся все три формы:
прямая ссылка на себя (`val parent: Node?`), ссылка через коллекцию (`val children: List<Node>`) и
взаимная рекурсия.

Заодно снят костыль `type != memberType`: свойство того же типа больше не выбрасывается из схемы
молча — теперь его есть чем описать.

### B2. Коллекция или примитив в ответе → `InvalidTypeForOpenApiType` — ✅ исправлено

```kotlin
get<List<User>>("/users") { ... }   // Invalid java.util.List<User> to build Object
get<String>("/ping") { "pong" }     // Invalid java.lang.String to build Object
```

`objectType()` (`OpenApiKType.kt:44`) требует, чтобы верхний уровень был объектом. Список в
ответе — абсолютно рядовой REST-кейс; сейчас его нужно оборачивать в wrapper-класс.

**Сделано**: у `OpenApiKType` появилась точка входа `type()`, которая описывает тип любой формы —
объект, коллекцию, enum, value class, примитив. `Router.addToPath` (`Router.kt`) описывает через
неё и ответ, и тело запроса: массив в теле (bulk create) ровно так же обычен, как и в ответе.
Параметры операции по-прежнему идут через `objectType()` — их разбирают по свойствам, так что
объект там нужен по существу, а не по случайности.

```json
"/users":{"get":{"responses":{"200":{"content":{"application/json":{
  "schema":{"nullable":false,"type":"array","items":{"$ref":"#/components/schemas/User"}}}}}}}}
```

### B3. value class и enum в ответе → `ClassCastException` — ✅ исправлено

`objectType()` делает безусловный `buildType(name, original) as Type.Object`
(`OpenApiKType.kt:48`), а `buildType` для value class разворачивает во вложенный тип
(`OpenApiKType.kt:68`), для enum — в `Type.String`:

```kotlin
@JvmInline value class Money(val amount: Long)
get<Money>("/price") { ... }   // Type$Number cannot be cast to Type$Object
get<Color>("/color") { ... }   // Type$String cannot be cast to Type$Object
```

**Сделано**: тем же `type()`, что и B2, — enum в ответе даёт `string` со списком значений, value
class даёт то, что он оборачивает. Сам `objectType()` контракт сохранил, но `ClassCastException`
больше не бросает: если тип описан не объектом, летит тот же внятный `InvalidTypeForOpenApiType`,
что и для коллекций.

Побочный эффект, который стоит отметить отдельно: value class теперь разворачивается везде, а не
только на верхнем уровне. Раньше свойство-value class уходило в ветку `buildObjectType` и
описывалось объектом (`{"price":{"type":"object","properties":{"amount":{"type":"number"}}}}`),
хотя `kotlinx.serialization` отдаёт голое значение (`{"price":1}`). Теперь спека совпадает с JSON.

Заодно перестал рефлексироваться во внутренности `Map` на верхнем уровне: раньше `objectType()` для
мапы описывал `entries`/`keys`/`size`/`values`, теперь мапа идёт по той же ветке, что и
свойство-мапа (какой она остаётся кривовато — это C3).

Тесты (на B2 и B3): `TopLevelTypeTest` — `List<T>`, `String`, enum и value class в ответе, `List<T>`
в теле запроса, value class в свойстве, и `objectType()` на каждом из этих типов с внятной ошибкой.

### B4. Raw-генерики и star-projection → `NullPointerException` — ✅ исправлено

`buildGenericTypes` (`OpenApiKType.kt:31`) делает `type.arguments[index].type!!`, а у
star-projection `type == null`:

```kotlin
typeOf<Generic<*>>().openApiKType.objectType()   // NPE
data class WithStar(val anything: List<*>)      // NPE
```

Аналогичные `!!` есть в ветке коллекций (`OpenApiKType.kt:93`) и map (`:110`, `:112`).

**Сделано**: `!!` убраны все, а неизвестный тип описывается пустой схемой — в OpenAPI это ровно
«что угодно». Для этого в `Type` добавлен вариант `Any`.

- `buildGenericTypes` пропускает параметр, для которого аргумента нет (raw-тип) или он не назван
  (star-projection), вместо `arguments[index].type!!`; свойства такого параметра описываются как
  «что угодно»;
- элемент коллекции: `List<*>` и коллекция, у которой ни аргументов, ни подходящего супертипа
  (`abstract class Bag : Collection<String>` — не `List` и не `Set`, а именно этот список
  проверяет старый код через `first {}`), дают `items: {}` вместо NPE и `NoSuchElementException`;
- `Map`: аргументы читаются через `getOrNull`; у `Map<*, *>` имени ключа нет, поэтому свойство не
  выдумывается вовсе;
- неразрешённый параметр типа (`T` там, где подстановки не нашлось) описывается как «что угодно», а
  не раскладывается по свойствам своей верхней границы;
- `enumConstants` у типа, который является подтипом `Enum<*>`, но не самим enum-классом, может быть
  `null` — теперь это отсутствие списка значений, а не NPE.

```json
"WithStar":{"type":"object","properties":{
  "anything":{"nullable":false,"type":"array","items":{"nullable":true}},
  "map":{"nullable":false,"type":"object","properties":{}},
  "generic":{"$ref":"#/components/schemas/Generic"}}}
```

Тесты: `UnknownTypeTest` — star-projection в параметре типа, в коллекции и в мапе, коллекция без
читаемого элемента, коллекция с элементом в супертипе, и целый документ со звёздочками, проходящий
валидатор.

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

### C1. Нет `required` — все поля опциональны — ✅ исправлено

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

**Сделано**: `required` появился в `SchemaObject`, а у `Property` — флаг `isRequired`. Источник
обязательности — сериализатор (C6): свойство обязательно, когда у сериализатора нет дефолта, на
который можно опереться.

```json
{"type":"object","properties":{...},"required":["id","name","age"]}
```

Два решения, которые стоит зафиксировать:

- **Nullable-свойство остаётся необязательным**, даже если дефолта у него нет. Строго говоря,
  `kotlinx.serialization` в дефолтной конфигурации потребует ключ `"nick": null` в теле; но при
  `explicitNulls = false` — нет, а `nullable: true` и так говорит клиенту всё, что ему нужно.
  Требовать явный `null` в теле — способ сломать существующих клиентов ради буквы; выбран мягкий
  вариант, тот самый «из non-null свойств», что и предлагал аудит.
- **Пустой `required` не сериализуется вовсе** — по спецификации массив должен быть непустым.

Заодно `required` у query-параметра тоже перестал выводиться из одной лишь nullability: параметр с
дефолтом теперь необязательный по той же причине, что и свойство с дефолтом.

Тесты: `SerializedShapeTest` — обязательность от дефолтов и nullability, объект без единого
обязательного поля, и тип без сериализатора (обязательность из nullability, больше не из чего).

### C2. Числа и форматы — ✅ исправлено

- `Int`, `Long`, `Short`, `BigDecimal` — всё `isSubtypeOf(Number)` → `"type":"number"`
  (`OpenApiKType.kt:24`). В OpenAPI есть `integer` c `int32`/`int64`.
- **`SchemaObject` не имеет поля `format` вообще.** Поэтому `UUID` — это просто `string`, а
  `date`/`date-time`/`email`/`binary` выразить нельзя даже через `customTypeDescription`:
  максимум, что можно получить, — безформатный `string`.
- Заодно в `SchemaObject` отсутствуют `title`, `description`, `default`, `oneOf`/`anyOf`,
  `minLength`/`maxLength`/`pattern`, `minimum`/`maximum`, `minItems`/`maxItems`/`uniqueItems`
  (`allOf` появился на этапе 2 — им заворачивается nullable-ссылка, см. C7).

`Type` (`OpenAPI.kt:170`) тоже не имеет места для формата — правку нужно делать в обеих моделях.

**Сделано**: `format` добавлен и в `SchemaObject`, и в `Type` (`String`, `Number` и новый
`Integer`), а имена форматов собраны в объекте `Format` — при желании любой другой можно задать
через `customTypeDescription`, поле в OpenAPI открытое.

| Kotlin | Было | Стало |
|---|---|---|
| `Int`, `Short`, `Byte` | `number` | `integer` / `int32` |
| `Long` | `number` | `integer` / `int64` |
| `BigInteger` | `number` | `integer` |
| `Float` / `Double` | `number` | `number` / `float`, `double` |
| `BigDecimal` | `number` | `number` (формата у неё нет) |
| `UUID` | `string` | `string` / `uuid` |
| `Duration` | `string` | `string` / `duration` |

Остальные перечисленные поля `SchemaObject` — `title`, `description`, `default`, ограничения длины
и диапазона — по-прежнему отсутствуют: это уже валидация (P1 в `issues.md`), а не соответствие
спеки реальности. `allOf` и `oneOf` появились по ходу C7 и C5.

### C3. `Map<K, V>` описывается неверно — ✅ исправлено

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

**Сделано**: заведён `Type.Map`, который в схему уходит именно так:

```json
"meta":{"nullable":false,"type":"object","additionalProperties":{"nullable":false,"type":"integer","format":"int32"}}
```

Тип ключа не описывается вовсе — ключи JSON-объекта строки, чем бы ключ ни был объявлен. У мапы с
неизвестным типом значения `additionalProperties` — пустая схема: «значения любые», а не «полей
нет».

### C4. `java.time.*` и `ByteArray` рефлексируются во внутренности — ✅ исправлено

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

**Сделано**: заведена таблица встроенных соответствий — `java.time.*` (`date`, `date-time`,
`time`, `duration`), `java.util.Date`, `ByteArray` → `string`/`binary`, `java.net.URI`/`URL` →
`string`/`uri`, `kotlin.uuid.Uuid`. Сопоставление идёт по полному имени класса, а не по самому
классу: `kotlinx.datetime` не зависимость этой библиотеки, но проект, который им пользуется,
заслуживает описанных дат. Всё прочее из `java.time` уходит в бесформатный `string` — это ближе к
правде, чем поля, которые у класса случайно оказались.

Ещё три случая того же рода:

- `Array<T>` и `IntArray` — не `Collection` в Kotlin, поэтому описывались своим единственным
  свойством `size`; теперь это массивы, с типом элемента;
- свойство, у которого свой сериализатор (`@Serializable(with = ...)`), описывается тем, что этот
  сериализатор пишет, если он пишет примитив — знает об этом именно он, а не рефлексия;
- `Any` — это «что угодно», а не объект без полей: теперь пустая схема.

**Не сделано** из предложенного: падать на неизвестном типе без свойств. `Unit` и любой пустой DTO
— ровно такие типы, и превращать их в падение на старте сразу после того, как этап 2 крэши убрал,
неправильно. Случаи, ради которых это предлагалось, закрывает таблица плюс `customTypeDescription`.

Тесты: `TypeFormatTest`.

### C5. `sealed class` → пустой объект — ✅ исправлено

```kotlin
sealed class Shape { data class Circle(val r: Double) : Shape(); data class Square(val a: Double) : Shape() }
get<Shape>(...)  // "schema":{"type":"object","properties":{}}
```

Ни `oneOf`, ни `discriminator` (`DiscriminatorObject` объявлен в `Components.kt:78` и не
используется). Полиморфные ответы описываются как пустой объект. То же для `Unit` —
`{"type":"object","properties":{}}` плюс `content` даже при 204.

**Сделано**: каждый подкласс описывается отдельной схемой, а сам sealed-тип — `oneOf` из них с
`discriminator`:

```json
"Payment":{"discriminator":{"propertyName":"type","mapping":{
   "card":"#/components/schemas/Card","cash":"#/components/schemas/Cash"}},
 "oneOf":[{"$ref":"#/components/schemas/Card"},{"$ref":"#/components/schemas/Cash"}]}
```

- `propertyName` — `type`: то поле, в которое `kotlinx.serialization` пишет тип значения;
- ключи `mapping` — serial-имена подклассов (`@SerialName`, иначе полное имя класса), то есть
  ровно то, что окажется в JSON. Mapping выписывается явно: имена компонентов наши, и угадать по
  значению нужную схему генератору неоткуда;
- иерархия **без** `@Serializable` описывается без дискриминатора — чем её подклассы различаются в
  JSON, не нам придумывать;
- sealed-тип, ссылающийся на себя (дерево выражений), описывается через `$ref`, как и всё
  остальное после C7.

В `SchemaObject` добавлен `oneOf`, `DiscriminatorObject.mapping` стал необязательным, а регистрация
компонента обобщена за пределы объектов.

`Unit` из этой находки остался как есть: `{"type":"object","properties":{}}` — честное описание
пустого тела. Отсутствие `content` у 204 — это C11 (коды ответов и content-type).

Тесты: `SealedTypeTest`.

### C6. Схема включает не то, что сериализуется — ✅ исправлено

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

**Сделано**: источник истины — сериализатор типа, когда он есть. `SerialDescriptor` знает, под
какими именами пишутся свойства, какие пишутся вообще, в каком порядке и какие можно не передавать.
Тот же DTO теперь описывается так, как и выглядит в JSON:

```
схема:      properties: user_id, kept   (в порядке объявления)
реальность: {"user_id":"1","kept":"k"}
```

Сразу закрылись все четыре пункта находки — `@SerialName`, `@Transient`, вычисляемые getter'ы,
порядок полей — и вдобавок дефолты, из которых берётся обязательность (C1).

Соответствие «элемент дескриптора → свойство» ищется по serial-имени: имя элемента сверяется с
`@SerialName` свойства, а типы по-прежнему берутся из рефлексии — без `KType` не построить ни
подстановку дженериков, ни `customTypeDescription`, ни имена компонентов.

Тип **без** сериализатора (не `@Serializable`) описывается рефлексией, как и раньше: алфавитный
порядок, Kotlin-имена, обязательность из nullability. Иначе библиотека перестала бы работать с
обычными классами, а параметры операций в проектах сплошь и рядом такие.

Тесты: `SerializedShapeTest`.

### C7. Нет `$ref` и `components` — ✅ исправлено

`toSchemaObject` (`OpenAPI.kt:113`) всегда инлайнит схему целиком. `addModel` (`OpenAPI.kt:81`)
умеет писать в `components.schemas`, но из Ktor-слоя не вызывается никогда — `components` в
сгенерированной спеке всегда пуст. Следствия: рекурсивные типы невозможны (B1), общий DTO в
20 эндпоинтах дублируется 20 раз, спека распухает, в Swagger UI нет раздела Schemas.

**Сделано**: объект, у которого есть исходный `KType`, описывается в `components.schemas` один раз,
а на месте использования стоит `$ref`. Это же — единственный способ описать рекурсию (B1).

```json
"components":{"schemas":{"Node":{"nullable":false,"type":"object","properties":{
  "name":{"nullable":false,"type":"string"},
  "children":{"nullable":false,"type":"array","items":{"$ref":"#/components/schemas/Node"}},
  "parent":{"nullable":true,"allOf":[{"$ref":"#/components/schemas/Node"}]}}}}}
```

Детали, каждая из которых стоила отдельного решения:

- **Nullable-ссылка.** В OpenAPI 3.0 всё, что стоит рядом с `$ref`, игнорируется, поэтому
  `{"$ref":..., "nullable":true}` не работает — ссылку приходится заворачивать:
  `{"nullable":true,"allOf":[{"$ref":...}]}`. Ради этого в `SchemaObject` добавлено поле `allOf`.
  Сама схема в `components` всегда non-nullable: она описывает тип, а право быть `null`
  принадлежит месту использования — один и тот же тип используется и так, и так.
- **Имена компонентов.** Собираются из simpleName класса и simpleName его аргументов:
  `Generic<String>` → `GenericString`, так что разные подстановки одного класса не затирают друг
  друга. Полное имя не годится: в ключах `components` допустимы только `[a-zA-Z0-9._-]`, а
  `javaType.typeName` вложенного класса содержит `$`. Совпадение имён у разных типов разводится
  счётчиком (`User`, `User2`) — имя за типом закрепляется один раз и дальше не меняется.
- **Имя берётся до описания свойств**, иначе тип, ссылающийся на себя, не нашёл бы имени схемы, в
  которую он же и попадает.
- **`addModel` перестал быть декорацией**: он кладёт схему под заданным именем и закрепляет это имя
  за типом, так что все последующие использования ссылаются именно на него, а не на имя, выведенное
  из класса.
- **Что по-прежнему инлайнится**: `Type.Object` без `returnType` — описание из
  `customTypeDescription` и объект-мапа. Опознать их не по чему, и общего имени у них нет.
  `customTypeDescription` проверяется раньше компонентов, так что заданное вручную описание
  подставляется на месте, как и раньше.

Тесты: `SchemaComponentTest` — рекурсия через коллекцию, взаимная рекурсия, nullable-ссылка, один
тип на двух эндпоинтах (одна схема, две ссылки), два разных типа с одинаковым simpleName,
`addModel` со своим именем, пустой документ без `components` вовсе.

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
   (рекурсия) и C7 (дублирование). Ключевая правка архитектуры. — ✅ сделано.
7. **Верхний уровень ответа любого типа** — `toSchemaObject` вместо `objectType()`: массивы,
   примитивы, enum, value class (B2, B3). — ✅ сделано (заодно и тело запроса).
8. **Убрать `!!`** в `buildGenericTypes`/коллекциях/map, star-projection → пустая схема (B4). —
   ✅ сделано.

### Этап 3 — привести смысл в соответствие

9. **`required` в `SchemaObject`** из non-null свойств (C1) — самая важная семантическая правка. —
   ✅ сделано.
10. **`format` в `SchemaObject` и в `Type`**, `integer` для целых, встроенные маппинги
    `java.time.*`/`UUID`/`ByteArray` (C2, C4). — ✅ сделано.
11. **`Map` → `additionalProperties`** (C3). — ✅ сделано.
12. **Источник истины — `kotlinx.serialization`** (`SerialDescriptor`) вместо `memberProperties`:
    сразу закрывает `@SerialName`, `@Transient`, порядок полей, дефолты и обязательность (C6, и
    сильно упрощает C1). Самая крупная, но и самая окупаемая переделка. — ✅ сделано (для типов без
    сериализатора рефлексия осталась запасным вариантом).
13. **`sealed class` → `oneOf` + `discriminator`** (C5). — ✅ сделано.

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
явным аргументом (включая подстановку проекций), value class как свойство (после этапа 2 — уже
и с правильным описанием, см. B3), слияние операций по одному пути и разным методам,
`customTypeDescription` для объектных типов, раздача `/openapi.json` и Swagger UI через
webjars — всё это генерируется и проходит валидатор.

## Где всё это оказалось после трёх этапов

Типичный DTO — с serial-именами, датой, мапой, enum и полиморфным полем — описывается так:

```json
"User":{"type":"object","properties":{
  "id":{"type":"string","format":"uuid"},
  "full_name":{"type":"string"},
  "age":{"type":"integer","format":"int32"},
  "registeredAt":{"type":"string","format":"date"},
  "nickname":{"nullable":true,"type":"string"},
  "roles":{"type":"array","items":{"type":"string","enum":["ADMIN","USER"]}},
  "meta":{"type":"object","additionalProperties":{"type":"string"}},
  "payment":{"$ref":"#/components/schemas/Payment"}},
 "required":["id","full_name","age","registeredAt","roles","meta","payment"]}
```

Осталось (этап 4, все — про метаданные и DSL, не про правдивость схем): описания ответов и
`operationId` (C8), проброс `tags` (C9), настраиваемые `info`/`servers` (C10), spec-модели
security и callbacks (A7–A10), header-параметры, content-type и per-route коды ответов (C11).
