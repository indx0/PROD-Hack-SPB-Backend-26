# MoverMate — moving without chaos

## 1. Problem

Moving is one of the most stressful events in a family's life. Belongings are packed into dozens of similar boxes, and it soon becomes difficult to remember where documents, chargers, clothes, or essentials are. After the move, boxes may remain packed for weeks. Finding a single item requires checking several boxes, and some items get lost or accidentally thrown away with the packaging. At the same time, organizational tasks need to be handled: booking movers, defrosting the refrigerator, disconnecting the internet, and gathering first-day items. Information is stored in notes, conversations, and the memories of those involved in the move. As a result, tasks are forgotten, and the current state of preparation is difficult to track.

## 2. Product goal

Create a service for planning a move, tracking boxes, and quickly finding items. The service should:

- Help plan tasks before, during, and after the move.
- Maintain an inventory of boxes and their contents.
- Allow items to be found by name.
- Generate labels with numbers and QR codes.
- Track the loading, delivery, and unpacking of boxes.
- Help prepare essential items.

The main focus is reliable item tracking, predictable behavior, and fast search.

## 3. Target audience

Individuals and families preparing to move who want to organize tasks, packing, and the subsequent unpacking of their belongings.

## 4. Core user scenarios

### Scenario 1. Planning a move

The user creates a move project, specifies the date and, if needed, addresses. They receive a predefined task plan organized by stage, add their own tasks, and set deadlines and assignees.

### Scenario 2. Packing and tracking items

The user creates boxes, specifies destination rooms, and records the contents. If needed, filling in the item list can be deferred.

### Scenario 3. Labeling and search

The user prints labels with numbers and QR codes. To find an item, they enter its name in search or open the box card using its QR code or number.

### Scenario 4. Tracking the move and unpacking

The user marks boxes as loaded and delivered, tracks their statuses, completes the first-day checklist, and monitors unpacking progress by room.

## 5. Functional requirements

### 5.1. Move project and tasks

- Create a project with a name, move date, and optional “from” and “to” addresses.
- Divide tasks into three stages:
  - Before the move: book movers, defrost the refrigerator, disconnect the internet, pack the first-day box, and disassemble furniture.
  - Moving day: load belongings by room, check that nothing has been forgotten, and hand over the keys.
  - After the move: unpack the first room, connect the internet, and put belongings away.
- Use predefined task templates.
- Add custom tasks.
- Specify deadlines and optional assignees.
- Mark tasks as completed.

### 5.2. Boxes and inventory

- Create a box with a unique number and destination room.
- Maintain a list of items inside each box.
- Quickly enter the contents.
- Allow entering the contents to be deferred with a subsequent reminder.
- Track box statuses:
  - Being packed.
  - Packed.
  - Loaded.
  - Delivered.
  - Unpacked.
- Delete boxes and individual items with confirmation.

### 5.3. Search and labels

- Search for items by name across the contents of all boxes.
- Display the box found, its destination room, and its current status.
- Search for a box by number.
- Generate a label with a large number, destination room, and QR code.
- Open the box card when its QR code is scanned.
- Print a label through a print page or PDF.
- Allow a label to be shown on a phone screen.

### 5.4. Tracking the move and unpacking

- A loading checklist by room.
- Box delivery marks.
- Display the number of boxes in each status.
- Display unpacking progress by room.
- A first-day checklist with essential items: toothbrushes, chargers, a kettle, and bed linen.
- Allow an item to be marked as lost.
- Suggest which boxes that have not been unpacked to search for a lost item, based on rooms and recorded contents.

### 5.5. Guides

Short reference materials:

- How to pack fragile items.
- What to carry personally rather than in the moving truck: documents and valuables.
- Which items should be packed last.
- Materials are stored locally in files or a database.

## 6. Additional features

### Smart assistant (ML)

### Room packing planning

The user describes a room in text or provides a photo. The service suggests a box count, item allocation, packing order, and items that can be thrown away or donated.

### Box inventory from a photo

The user photographs the contents before closing the box. The service automatically compiles a list of items.

### Personalized checklists

Generate checklists tailored to the move: with children, with pets, or to another city.

## 7. Interface requirements

The interface should be simple and minimalistic. 3–4 main screens are sufficient:

- Move plan: stages, tasks, deadlines, and assignees.
- Boxes and search: a list of boxes, contents, statuses, and item search.
- Labels: generating, viewing, and printing QR labels.
- Progress: loading, delivery, and unpacking status.

## 8. Nonfunctional requirements and constraints

### Ease of setup

- Installation and startup without complex external configuration or integrations.
- Local generation of QR codes and labels without external services.
- Local storage of task templates, checklists, and guides.

### Reliability

- Box numbers are unique.
- Scanning a QR code again opens the existing box without creating duplicates.
- Status changes follow only valid transitions: for example, a box cannot be unpacked before it is delivered.
- Identical search queries return identical results.
- Data is preserved if the page is accidentally refreshed.

### Privacy and security

- Personal data is not required.
- Addresses are optional and used only within the move project.
- There are no hidden transfers of data to third parties.

### Performance

- Support for hundreds of boxes and thousands of item records.
- Fast search.
- Filtering or pagination of long lists.

### Verification and observability

- A log of important events: project and box creation, status changes, and task completion.
- A simple service health check through a page or health indicator.
