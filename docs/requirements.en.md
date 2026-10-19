# Requirements implementation

The primary audience is companies relocating offices and departments. Employees describe boxes, movers mark loading and delivery, and the coordinator monitors overall progress.

The basis is the specification “MoverMate — moving without chaos”, §4–8. The first day is managed through regular tasks; guides are implemented in the mobile application.

## Core scenarios

| Specification requirement | Backend implementation |
| --- | --- |
| §5.1. Project and predefined plan (F-01–F-03) | Name, date/time, optional addresses. The project, participants, folders, and selected task sets are created in a single transaction. Without explicit task selection, the plan is empty. Personal sets contain tasks from any stage, belong to the account, and are available across devices. Shared and custom individual templates can be applied through `/api/task-templates`; changing a template does not change created tasks, and reapplying returns 409. |
| §5.1. Custom tasks, deadlines, and completion (F-04–F-06) | Creation, editing, deletion, and filters by stage and completion. A deadline is FIXED or RELATIVE with an offset in days; no deadline means FIXED with null. A legacy PATCH with dueDate enables FIXED; explicit null removes the deadline. An assignee can be set using a participant's UUID; the name is taken from the profile. Free-form names are supported for compatibility. When access is revoked, assignments are cleared without deleting tasks. Completion is logged and can be undone. |
| Addition to §5.1. Folders and linked items | Folders with a name and color, and counters for all and completed tasks. A task can be in one folder and reference one item from the same project. Deleting the folder or item preserves the task and clears the corresponding link. |
| §5.2. Boxes and contents (F-07–F-09) | The number is assigned by the client and is unique within the project. The room belongs to the same project. Items have `quantity`, `firstDay`, and packed/unpacked marks. Batch accepts 1–100 names (`names`) or objects (`items`); the entire list is saved or rolled back. Adding an item, changing its quantity, and deleting it are allowed only in `PACKING`; a new quantity resets the packed mark. The name, `lost`, and `firstDay` can be changed in any status. |
| §5.2. Deferred inventory, statuses, deletion (F-10–F-12) | The `contentsDeferred` flag, filtering, and a counter for such boxes; adding items does not clear the flag by itself. Five sequential statuses. `PACKED` requires a nonempty box and every item to be marked as packed; `UNPACKED` requires every item to be marked as unpacked. All database records are checked, including `lost`. The card and list contain counters and readiness indicators that take the status into account. Deleting a box deletes its contents. The reminder and deletion confirmation are implemented in the mobile application. |
| §5.3. Search (F-13–F-14) | Case-insensitive search by part of the name; the result contains the box number, room, and status. A box can be found by its exact number. For unchanged data, the result order is stable. |
| §5.4. Loading, delivery, and progress (F-19–F-21) | Box lists with room and status filters allow a loading checklist to be assembled. Loading/delivery marks are status transitions. The summary contains counts by status and unpacking progress for rooms with boxes. |
| §5.4. First day (F-22) | Managed through regular tasks linked to items. An explicit `includeFirstDayChecklist: true` creates four tasks: toothbrushes, chargers, a kettle, and bed linen. Old entries have been migrated to the “Personal tasks” folder, preserving completion and links. The former API is available only for reading the archived snapshot; writes return 410. |
| §5.4. Lost items (F-23–F-24) | The `lost` flag, a lost-item filter, and a list of candidate boxes. The candidate search takes recorded contents and room into account and excludes unpacked boxes; these are rules, not ML. |

CRUD for personal sets, application without duplicates, and optional selection are described in Swagger (`/api/task-sets`).

### Folders and initial plan

- `/api/move-projects/{projectId}/task-folders`: creation, reading, renaming, changing color, and deletion. The UUID is assigned by the client; the name is 1–80 characters after trimming whitespace; colors are `mint`, `cyan`, `coral`, `sand`, `lilac`, `forest`. Identical names are allowed. Repeating creation with the same UUID and initial data succeeds if the folder has not changed; a conflict returns 409.
- Tasks are filtered by `folderId`, stage, and completion. `folderId`, `assigneeId`, and `linkedItemId` are set when creating and updating a task; omission in PATCH preserves the value, and explicit `null` clears the link. The assignee is the project creator or a participant. One item can be linked to multiple tasks; the link does not change its `firstDay` flag.
- Project creation accepts `folders` and `taskSetApplications: [{taskSetId, folderId}]`. The destination folder must be declared in the same request; an error rolls back the entire creation. Without these fields, folders and sets are not added.
- A set can be applied later through `PUT /api/move-projects/{projectId}/task-sets/{setId}` with an optional `folderId`. A repeat does not create duplicates; a different explicitly specified folder on a repeat returns `409 TASK_SET_ALREADY_APPLIED`. Subsequent changes to the set do not change created tasks.
- The legacy `taskSetIds` is supported but cannot be combined with nonempty `taskSetApplications`. `includeFirstDayChecklist` is deprecated: omission or `false` adds nothing; `true` creates four regular `BEFORE_MOVE` tasks without a deadline in the “Personal tasks” folder.

The legacy `GET /api/move-projects/{projectId}/first-day-checklist` reads an immutable archive: changes to tasks, items, and rooms do not affect it. Old entries are transferred once by a migration; tasks deleted after the transfer are not recreated.

### Collaboration and archiving

Relative deadlines and atomic rescheduling of the move date are supported for explicitly selected incomplete tasks. A regular project PATCH does not change deadlines.

Full contracts are available in [Swagger](https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/docs).

Shared projects are accessible to the creator and existing users added by email or UUID.
No confirmation is required; everyone has the same permissions, including adding participants, archiving,
restoring, and deleting. The project list includes personal and shared projects without duplicates; search,
suggestions, the checklist, and statistics also check participation. `ownerId` is only information about the creator.
The actual actor's UUID is stored in the log.
When a participant is removed, their task assignments are cleared. The creator's access cannot be revoked.

A move stores nullable `archivedAt`; the API response contains the timestamp and a computed `archived`.
`PUT /api/move-projects/{projectId}/archive` archives at any stage,
and `DELETE /api/move-projects/{projectId}/archive` restores. Both return 200 with the project.
Repeating the current state does not change the timestamp or add an event. An inaccessible or missing project returns 404.
Archiving preserves all data: reading, search, statistics, and the log remain available. All changes to the project,
rooms, boxes, items, tasks, and folders, including moves, statuses, corrections, applying a template,
setting/clearing a link, and permanent project deletion, return 409 `DATA_CONFLICT` until restoration.
Even repeating a modifying request in the archive is prohibited; the exceptions are the archive/restore operations themselves.
Deprecated write operations for the separate first-day checklist return `410 FIRST_DAY_CHECKLIST_RETIRED`, including in an archived project.
The `PROJECT_ARCHIVED` and `PROJECT_RESTORED` events contain the actor, timestamp, and project UUID and are saved
in a single transaction with its state. The project list accepts `archived=true/false`; omitting
the parameter returns all projects. The filter is applied in the database before pagination, including the total count.

A room stores an optional `iconKey` — a nonempty string key of up to 64 characters.
On creation, omission or `null` means no explicit selection. In PATCH, omission preserves
the icon, explicit `null` clears the selection, and a string sets a new one. An empty or whitespace-only string or a length
greater than 64 characters returns 422. Renaming preserves the selected icon; for old rooms, migration
0015 leaves `NULL`, without determining the icon from the name.

Mobile client keys: `kitchen` — kitchen, `bed` — bedroom, `sofa` — living room, `bath` — bathroom,
`book` — study, `key` — hallway, `heart` — children's room, `sun` — balcony. `home` is the fallback icon
for a missing key or one unknown to the client. The server stores the key unchanged and without
a closed enumeration, so new client versions can add icons.
The room request/response field is called `iconKey`, not `icon`.

Boxes, items (including search), search suggestions, and the room summary
return the current `roomIconKey` from the room. The old checklist returns the icon from the archived snapshot.
Box filtering remains based on `roomId`. The mobile application must send the selection and read
the key on all screens, including labels; images and the `home` fallback are displayed by the client.

A box stores `title` (up to 255 characters, leading and trailing whitespace trimmed, default `""`),
`fragile`, `firstDay`, and `carryWithMe` (default `false`). Titles can be identical and do not replace the number.
In PATCH, omitting a field preserves the value, `false` clears the flag, and `title: ""` clears the title;
explicit `null` is prohibited and returns 400; exceeding the length returns 422. These fields can be edited in any status
and do not change the contents, statuses, items, or checklist. A box's `firstDay` is independent of the same-named item field.
`GET /api/projects/{projectId}/boxes` accepts exact filters `fragile`, `firstDay`, `carryWithMe`:
an omitted parameter does not restrict the selection; `true` and `false` values are combined with AND
with the other filters. The page count takes all filters into account. Searching for a box by number remains exact;
title search `q` has not yet been added and requires coordination with the mobile team.

An item is moved through a separate `PUT /api/move-projects/{projectId}/boxes/{sourceBoxId}/items/{itemId}/box`
with `targetBoxId`. Both boxes must be in `PACKING` and belong to the same project accessible to the participant.
The UUID, quantity, marks, and task links are preserved. A repeat when the item is already in the target box
succeeds without changing timestamps, even after the box is packed; a third box means a 409 conflict.

An incorrect status is corrected through a separate `POST /api/projects/{projectId}/boxes/{boxId}/status/correction`
with `expectedStatus`, `targetStatus`, and `confirmed: true`. A correction returns to any earlier stage
and does not allow forward transitions. A mismatch between the current and expected status returns 409. If the target status
is already set, the request succeeds without changes to timestamps, items, or the log, even with a stale expected
status; required fields and confirmation are also needed for repeats.
`packedAt` marks are preserved. Returning to `PACKING`, `PACKED`, or `LOADED` clears `unpackedAt` for all items;
`UNPACKED → DELIVERED` preserves them and allows incorrect marks to be cleared through a separate item PATCH.
Returning to `PACKED` or `LOADED` requires a nonempty box and all records to be packed, including lost items;
otherwise, `PACKING` is available. Names, quantity, `lost`, `firstDay`, and task links are preserved.
A regular status PATCH still accepts only the next stage or a repeat of the current one.
The `BOX_STATUS_CORRECTED` event contains the actor, timestamp, `previousStatus`, and `newStatus`; for other
event types, these fields are `null`. The client must show a confirmation describing the consequences for unpacking.

## Reliability and limitations — specification §8

- **Password:** at least 1 character when registering or changing a password. Empty and whitespace-only strings are rejected; the password is stored as a hash.
- **Access:** the current user is determined from the JWT. The creator and added participants have equal permissions within a specific project. Those users' other projects remain inaccessible.
- **Integrity:** database constraints protect box numbers and room names within a project; data changes and event recording take place in a single transaction.
- **Repeats:** repeating the current box status or completion mark does not create an additional event or change timestamps.
- **Long lists:** shared `page/size` pagination, a size of up to 100, and filters. There are checks for batch writes and deletion of large sets; latency under load has not yet been measured.
- **Storage:** data persists in PostgreSQL across page refreshes and application restarts. The schema is updated by Liquibase migrations; Hibernate uses `ddl-auto=validate`.
- **Observability:** a log of project/box creation, status changes, and task completion; readiness also checks the database. Technical API, JVM, connection pool, and LLM metrics are available for Sage to collect through a protected endpoint.
- **Local reference data:** task templates and the basic first day do not require third-party services.
- **ML inventory:** the photo is sent to an external LLM Proxy and the model provider at the request of an authorized user. If the key is missing or the LLM is unavailable, recognition returns an error; other features do not require the LLM.
- **ML limit:** by default, 15 requests per rolling 60 seconds per user, across all ML scenarios combined. Exceeding the limit returns `429 ML_RATE_LIMIT_EXCEEDED` with `Retry-After`. Parameters: `ML_RATE_LIMIT_MAX_REQUESTS`, `ML_RATE_LIMIT_WINDOW_SECONDS`; the counter is stored in the memory of a single backend instance.

## Additional ML features — specification §6

| Feature | Backend implementation |
| --- | --- |
| Single-item description from a photo | `POST /api/ml/item-description` returns the name of the main item and suggested flags. |
| Multiple-item inventory from a photo | `POST /api/ml/items-description` returns `items`: 1–100 confidently recognized items with individual flags. Identical items are represented as separate entries; the model's order is preserved, and there is no quantity field. |
| Checking unpacked items against the inventory | `POST /api/move-projects/{projectId}/ml/boxes/{boxId}/unpacking-verification`: one photo is compared against the selected box's current inventory of 1–100 items. Returns `matched`, `notSeen`, and `unmatched`. |
| Room packing planning | `POST /api/ml/room-packing-plan` accepts a room description and/or photo and returns the box count, item allocation, packing order, and candidates for donation/disposal. |
| Reviewing and saving the result | The user can correct the name and the `fragile`, `first_day`, `carry_personally` flags, then save selected items through the existing API. Recognition does not fill the box itself; flags are editable and returned when reading and searching for items. |

Recognition and verification routes require JWT and accept one JPEG, PNG, or WebP photo
of up to 10 MiB. Photos and recognition results are not stored separately. A complete inventory and exact
item counts are not guaranteed.

Single-item and list recognition do not require a project and do not access the project database.
The former routes `/api/move-projects/{projectId}/ml/item-description` and
`/api/move-projects/{projectId}/ml/items-description` have been removed; clients need to switch to `/api/ml/...`.
Unpacking verification requires the JWT of the project owner or a participant and retains the route with `projectId` and `boxId`.

Verification is available in any box status. The backend checks project access and whether
the box belongs to the project, snapshots the inventory in a short read-only transaction, and ends it before calling the LLM.
The photo and item names with identifiers are sent to the LLM. An empty inventory returns
`422 EMPTY_INVENTORY`; more than 100 items returns `422 INVENTORY_TOO_LARGE`; the list is not truncated.
Matching takes the meaning of names and visible characteristics into account. Each visible item
corresponds to at most one record; identical instances are counted separately, in inventory order.
An ambiguous match remains in `unmatched`. `notSeen` means “not found in this
photo”, rather than a confirmed loss; `unmatched` does not prove that an item is extra.
The report contains `boxId`, `matched: [{itemId, name, photoDescription}]`,
`notSeen: [{itemId, name}]`, `unmatched: [name]`.
The report is not saved; statuses and `lost` flags do not change. Inventory changes while
waiting for the LLM are not included in the report. An unrecognizable photo returns
`422 IMAGE_NOT_RECOGNIZED`. Identifiers outside the inventory/duplicate identifiers and an invalid model response
are rejected with `502 LLM_INVALID_RESPONSE`. The wait is up to 120 seconds; the recommended
mobile client timeout is 150 seconds. Damage assessment and comparison against the original photo are not performed.

### Room packing planning

`POST /api/ml/room-packing-plan` requires JWT; a project and room in the database are not required.
Two request formats:

- `application/json`: `{"description":"Kitchen: dishes, books, and a kettle; the kettle is needed on the first day"}`.
- `multipart/form-data`: one `photo` (JPEG/PNG/WebP up to 10 MiB), a text field
  `description`, or both sources. The description is limited to 4000 characters after trimming leading and trailing whitespace.

At least one source is required. An empty description without a photo returns `422 ROOM_INPUT_REQUIRED`;
exceeding the length returns `422 DESCRIPTION_TOO_LONG`. An empty or invalid file is rejected
by the existing photo validator, even if text is present. Repeated and unknown multipart fields
are rejected with `400 INVALID_MULTIPART`.

The response contains `boxCount`, `boxGroups: [{label, count, items, packingAdvice}]`,
an ordered `packingOrder`, `declutteringSuggestions: [{item, action, reason}]`,
and `assumptions`. `action` is `donate` or `discard`; the candidate list can be empty.
Each list contains no more than 100 entries. The backend calculates the total `boxCount` as
the sum of the groups' positive integer `count` values, up to a maximum of 100 boxes. Names are limited to
255 characters, explanations to 1000. For a successful plan, the groups, the items in each group,
the sequence of actions, and the assumptions are nonempty.

The count is approximate: the estimate is based on boxes of about 40 × 30 × 30 cm with a target
load of up to 10 kg; the model states this in `assumptions`. Heavy and fragile items are separated,
and first-day items are packed last and separately. Large furniture and appliances
are described in preparation steps rather than as contents of regular boxes.
Text can clarify items not visible in the photo. Hidden items, exact weight, and dimensions
cannot be determined from a photo. Candidates for donation/disposal require user review;
appearance alone does not mean that an item is unnecessary.

The text and/or photo are sent to an external LLM Proxy in a single request. They and the plan are not
stored separately; no boxes or items are created. The wait is up to 120 seconds; the recommended client
timeout is 150 seconds. Insufficient data or model refusal returns `422 ROOM_PLAN_NOT_GENERATED`;
an invalid response returns `502 LLM_INVALID_RESPONSE`; unavailability/overload returns `503`, and a timeout returns `504`.
The proxy response is limited to 64 KiB. Quality evaluation on real rooms is performed separately.
Personal checklist generation has not yet been implemented.

## Additional features — Sage and Grafana monitoring

Sage and Grafana are used to monitor operation. The backend publishes technical metrics through Spring Boot Actuator
and Micrometer, Sage collects them, and Grafana displays them on dashboards.
This allows tracking load, latency, API errors, memory usage, and technical failures when calling the LLM

### Metrics collection

Metrics are available through `GET /actuator/prometheus`. The endpoint is protected by a separate `METRICS_SCRAPE_TOKEN`, passed in a header:
Sage collection and Grafana dashboard configuration are performed separately from backend deployment.

### Metrics in Grafana

Grafana currently presents the following indicators:

| Indicator | What it shows | What it is used for |
| --- | --- | --- |
| Scrape availability | Whether metrics were successfully collected from the backend: `up = 1` — collection succeeded, `up = 0` — failed. | Helps detect lost connectivity to the application or a metrics collection problem. |
| API load (RPS) | The number of HTTP requests per second. | Helps assess request intensity and correlate increasing load with latency and errors. |
| API server errors | API responses with `5xx` codes. | Helps detect request processing failures and assess their trends. |
| JVM memory usage | The amount of memory used by the Java application. | Helps track resource consumption and detect sustained growth in memory usage. |
| Technical LLM failures | Unsuccessful technical outcomes of LLM operations, including timeouts, unavailability, external service errors, invalid responses, and overload. | Allows the reliability of ML features to be monitored separately. |
| p95 response time by route | The 95th percentile of HTTP request duration, broken down by route. | Helps find slow routes: about 95% of measured requests complete within the indicated time. |
| API request errors | The number and trends of `4xx` and `5xx` responses, broken down by HTTP method, route, HTTP status, and machine-readable error code, such as `INVALID_MULTIPART`. Metric: `movermate_http_errors_total`. | Helps find problematic requests, distinguish error causes, and assess their frequency. |


Metrics scrape availability does not replace an application readiness check. This uses `GET /actuator/health/readiness`, which takes the PostgreSQL connection into account.

Technical success of an LLM operation does not guarantee correct item recognition or recommendation quality. A response indicating that the image cannot be recognized should also be distinguished from a technical failure.

### Dashboard in Grafana
![Dashboard](/docs/images/grafana_dashboard.png)

### Dashboard in Sage (API load in RPS)
![Dashboard](/docs/images/sage_dashboard.png)

## Implemented in the mobile application

| Specification section | Implementation |
| --- | --- |
| §5.2. Deferred contents reminder | Implemented in the mobile application; the backend stores the `contentsDeferred` flag and allows boxes to be filtered by it. |
| §5.2. Deletion confirmation | The mobile application shows a confirmation before sending a deletion request to the API. |
| §5.3. QR, label, printing/PDF, and display on the phone (F-15–F-18) | Label generation and display, QR scanning, and printing are implemented in the mobile application. The backend returns an existing box by UUID. |
| §5.5. Three local guides (F-25–F-27) | Guides on packing fragile items, transporting documents and valuables, and items to pack last are implemented in the mobile application. |

The item flags `fragile`, `first_day`, `carry_personally` are returned in `flags`.
For compatibility, `firstDay` remains a second representation of the same first-day flag.
In PATCH, `flags: []` clears all flags; `firstDay: false` clears
only that flag. Task links to an item do not depend on these fields. A contradiction between two explicitly provided representations returns 422;
the entire request, including a batch, is rejected without partial changes.

## What to read next

| Question | Document |
| --- | --- |
| Where can I open the API? | [Swagger](https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/docs) |
| How is the system structured, and what stack does it use? | [RFC](../RFC.en.md) |
| Where are the reports, and how do I run the tests? | [Tests and reports](../TESTING.en.md) |
