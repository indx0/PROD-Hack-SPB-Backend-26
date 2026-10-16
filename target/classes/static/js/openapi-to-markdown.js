(function () {
    "use strict";

    const methods = [
        "get", "post", "put", "patch",
        "delete", "head", "options", "trace"
    ];

    function resolvePointer(document, reference) {
        if (!reference.startsWith("#/")) {
            throw new Error(`Не поддерживается внешняя ссылка: ${reference}`);
        }

        return reference.slice(2).split("/").reduce((current, segment) => {
            const key = segment.replace(/~1/g, "/").replace(/~0/g, "~");

            if (current == null ||
                !Object.prototype.hasOwnProperty.call(current, key)) {
                throw new Error(`Не найдена ссылка в OpenAPI: ${reference}`);
            }

            return current[key];
        }, document);
    }

    function expand(document, value, active = new Set()) {
        if (Array.isArray(value)) {
            return value.map(item => expand(document, item, active));
        }

        if (value === null || typeof value !== "object") {
            return value;
        }

        if (typeof value.$ref === "string") {
            const reference = value.$ref;

            if (active.has(reference)) {
                return {
                    type: "object",
                    description: `Рекурсивная модель ${reference.split("/").pop()}`
                };
            }

            const nextActive = new Set(active);
            nextActive.add(reference);

            const resolved = expand(
                document,
                resolvePointer(document, reference),
                nextActive
            );

            const rest = {...value};
            delete rest.$ref;

            return {...resolved, ...expand(document, rest, active)};
        }

        return Object.fromEntries(
            Object.entries(value).map(
                ([key, item]) => [key, expand(document, item, active)]
            )
        );
    }

    function appendContent(lines, document, content, emptyMessage) {
        const variants = Object.entries(content || {});

        if (variants.length === 0) {
            lines.push(emptyMessage);
            return;
        }

        for (const [mediaType, media] of variants) {
            lines.push(`Тело (${mediaType}):`);

            if (media.schema) {
                lines.push(
                    "```json",
                    JSON.stringify(expand(document, media.schema), null, 2),
                    "```"
                );
            } else if (media.example !== undefined) {
                lines.push(
                    "```json",
                    JSON.stringify(media.example, null, 2),
                    "```"
                );
            } else {
                lines.push("Структура тела не описана в OpenAPI.");
            }
        }
    }

    function format(document) {
        if (!document || typeof document !== "object" || !document.paths) {
            throw new Error("Ожидался OpenAPI JSON с полем paths");
        }

        const lines = [
            `# ${document.info?.title || "Описание API"}`,
            ""
        ];

        const paths = Object.entries(document.paths)
            .sort(([a], [b]) => a.localeCompare(b));

        for (const [path, pathItem] of paths) {
            for (const method of methods) {
                const operation = pathItem[method];
                if (!operation) continue;

                lines.push(`## ${method.toUpperCase()} ${path}`);

                if (operation.summary) {
                    lines.push(operation.summary);
                }

                if (operation.description) {
                    lines.push(operation.description);
                }

                const security = operation.security ?? document.security ?? [];

                if (security.length > 0) {
                    lines.push(
                        `Авторизация: ${security.flatMap(Object.keys).join(", ")}`
                    );
                }

                const parameters = [
                    ...(pathItem.parameters || []),
                    ...(operation.parameters || [])
                ];

                if (parameters.length > 0) {
                    lines.push("Параметры:");

                    for (const rawParameter of parameters) {
                        const parameter = expand(document, rawParameter);
                        const type = parameter.schema?.type || "тип не указан";
                        const required = parameter.required
                            ? ", обязательный"
                            : "";

                        lines.push(
                            `- ${parameter.in} ${parameter.name}: ` +
                            `${type}${required}. ${parameter.description || ""}`
                        );
                    }
                }

                if (operation.requestBody) {
                    const requestBody = expand(
                        document,
                        operation.requestBody
                    );

                    lines.push(
                        requestBody.required
                            ? "Запрос (обязательное тело):"
                            : "Запрос:"
                    );

                    appendContent(
                        lines,
                        document,
                        requestBody.content,
                        "Тело запроса не описано в OpenAPI."
                    );
                }

                lines.push("Ответы:");

                const responses = Object.entries(operation.responses || {})
                    .sort(([a], [b]) =>
                        a.localeCompare(b, undefined, {numeric: true})
                    );

                if (responses.length === 0) {
                    lines.push("Ответы не описаны в OpenAPI.");
                }

                for (const [status, rawResponse] of responses) {
                    const response = expand(document, rawResponse);

                    lines.push(
                        `### ${status} — ${response.description || "Без описания"}`
                    );

                    appendContent(
                        lines,
                        document,
                        response.content,
                        status === "204"
                            ? "Тело отсутствует."
                            : "Тело не описано в OpenAPI."
                    );
                }

                lines.push("");
            }
        }

        return lines.join("\n").trim();
    }

    window.OpenApiMarkdown = {format};
})();

(function () {
    "use strict";

    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Загрузка API...";
    button.disabled = true;

    const toolbar = document.createElement("div");
    Object.assign(toolbar.style, {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        width: "100%",
        minHeight: "72px",
        padding: "12px 16px",
        background: "#303030",
        borderBottom: "1px solid #444"
    });

    Object.assign(button.style, {
        padding: "14px 24px",
        minHeight: "48px",
        maxWidth: "calc(100% - 32px)",
        fontSize: "16px",
        border: "0",
        borderRadius: "8px",
        background: "#49cc90",
        color: "#111",
        fontWeight: "600",
        cursor: "pointer",
        boxShadow: "0 2px 12px #0003"
    });

    toolbar.appendChild(button);
    document.body.prepend(toolbar);

    let apiText;

    fetch("/v3/api-docs", {cache: "no-store"})
        .then(response => {
            if (!response.ok) {
                throw new Error(`OpenAPI вернул ${response.status}`);
            }
            return response.json();
        })
        .then(spec => {
            apiText = window.OpenApiMarkdown.format(spec);
            button.textContent = "Скопировать API";
            button.disabled = false;
        })
        .catch(error => {
            console.error("Не удалось загрузить описание API", error);
            button.textContent = "Ошибка загрузки API";
        });

    button.addEventListener("click", () => {
        navigator.clipboard.writeText(apiText)
            .then(() => {
                button.textContent = "Скопировано";
                setTimeout(() => {
                    button.textContent = "Скопировать API";
                }, 1500);
            })
            .catch(error => {
                console.error("Не удалось скопировать API", error);
                button.textContent = "Ошибка копирования";
            });
    });
})();
