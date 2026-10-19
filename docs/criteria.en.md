# Criteria for Backend judges

| Criterion | 0 points | 1 point | 2 points |
| --- | --- | --- | --- |
| Architectural decisions | The document has not been prepared, at least one of the 5 mandatory stages is missing, or the prepared document does not match the system actually developed | The document has been prepared and the developed system matches it, but the stages contain inaccuracies: — unjustified decisions — decisions that do not meet the requirements — requirements have been gathered incorrectly | The document has been prepared, the developed system matches it, requirements have been gathered and described correctly, and all decisions are justified and fully meet the requirements |
| Ease of evaluation | No CI/CD; builds and deployment are manual | Basic data is available, but it is incomplete or requires manual additions. Evaluation takes a long time | A complete dataset, automated scenarios, and clear instructions. Evaluation takes minimal time |
| Functionality | Evaluation could not be performed; Swagger or documentation is missing | Basic automation. Builds and deployment run from the pipeline | Full automation. Builds, deployment, and linting/tests run from the pipeline |
| Testing | No tests | There are unit tests covering the core functionality. (There is a coverage report that makes it clear which functionality is covered) | There are integration tests covering the main scenarios. (There is a coverage report that makes it clear which scenarios are covered) |
| Overall impression of this area | The project leaves a negative impression | A neutral impression; the project does not stand out | The project leaves a positive impression |

# Criteria for Mobile judges

| Criterion | 0 points | 1 point | 2 points |
| --- | --- | --- | --- |
| Guidelines and compliance | Significant noncompliance with the guidelines | Minor noncompliance with the guidelines | Full compliance with the guidelines |
| Design system | No component library | Compliance with the component library on all screens, but with errors | Compliance with the complete component library |
| Functionality | No APK/IPA in the repository, no backend connection, or a partial backend connection with bugs | Partial backend connection or functionality with errors/bugs | Everything works with minor errors/bugs |
| Code understanding | A complete lack of understanding of the codebase (less than 50% correct answers) | Partial understanding of the codebase (50–90% correct answers) | A solid understanding of the codebase (90–100% correct answers) |
| Error handling | No error handling or input field validation; application crashes and screen freezes occur | No error handling or input field validation; the application runs stably | All errors and invalid values are handled correctly; the application runs stably |

# Criteria for Frontend judges

| Criterion | 0 points | 1 point | 2 points |
| --- | --- | --- | --- |
| Functionality | The deployed frontend has no meaningful functionality | The deployed frontend has disruptive bugs or some functionality is unfinished | All declared functionality works in the deployed frontend |
| API usage | The interface does not call the API; the frontend is not connected to the backend | Some data in the interface is inconsistent with the backend | The interface actively uses the API and is consistent with backend data |
| Usability (UX) | The interface is confusing and difficult to use | The interface is logical but needs improvement | An intuitive interface |
| Responsiveness | Responsive design has not been addressed, which makes the interface difficult to use | Responsive design is implemented but has shortcomings | The interface is convenient to use on both a laptop and a phone |
| Visual appeal | The design is outdated; elements do not work together | The design is functional but looks visually inconsistent | A modern, visually harmonious interface |

# Criteria for Product judges

| Criterion | 0 points | 1 point | 2 points |
| --- | --- | --- | --- |
| Completeness of functionality | Core features are not implemented | Key features are implemented, but the product is incomplete | All declared features work, and the main scenarios are covered |
| Development of business scenarios | Business scenarios have not been developed | Scenarios are partially developed but lack detail | Fully developed business scenarios with user stories |
| Presentation of business value | The team did not present the business value | The team presented the business value but without supporting arguments | The team presented the business value and development potential with justification |
| Additional features | Additional features are not implemented | There are additional features, but they do not add value | Additional features are well implemented and address specific needs of the target audience |
| Overall impression of this area | The project leaves a negative impression | A neutral impression; the project does not stand out | The project leaves a positive impression |

# General criteria

| Criterion | 0 points | 1 point | 2 points |
| --- | --- | --- | --- |
| Presentation structure | An illogical structure; slides are overloaded. | Logical, but without detail. | Clear, with a demonstration of the product in action. |
| Overall impression of the project | The project leaves a negative impression. | A neutral impression; the project does not stand out. | The project leaves a positive impression. |

Projects must meet a number of mandatory criteria. Failure to meet them results in penalties that reduce the points earned.

| Criterion | Penalty |
| --- | --- |
| The solution is not deployed | -20% of points |
| Failure to meet the minimum set of criteria described in the case | -30% of points |
