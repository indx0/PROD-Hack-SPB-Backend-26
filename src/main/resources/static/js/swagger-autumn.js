(function () {
    "use strict";

    function mount() {
        if (document.getElementById("swagger-autumn-toggle")) return;
        if (!document.querySelector('meta[name="viewport"]')) {
            const viewport = document.createElement("meta");
            viewport.name = "viewport";
            viewport.content = "width=device-width, initial-scale=1";
            document.head.appendChild(viewport);
        }
        const storageKey = "movermate.swagger.autumn";
        const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
        const button = document.createElement("button");
        button.id = "swagger-autumn-toggle";
        button.type = "button";
        // Local vector shapes: no images, fonts or scripts are fetched from third parties.
        const shapes = [
            '<path d="M31 4 36 17 46 12 43 24 56 26 45 35 49 45 35 43 32 59 28 59 29 43 16 46 19 36 7 28 21 25 18 14 28 19Z"/>',
            '<path d="M31 5C14 9 23 17 13 19S10 31 17 32C6 40 17 46 26 43L30 59 33 59 34 43C46 48 56 37 46 32 57 28 51 19 44 19 49 9 38 12 31 5Z"/>',
            '<path d="M49 7C28 5 8 17 13 34 16 44 29 49 40 39 48 31 51 20 49 7Z"/>'
        ];
        const icon = '<svg viewBox="0 0 64 64" aria-hidden="true" focusable="false">' + shapes[0] +
            '<path d="M31 52V26M31 39 21 31M32 35 41 27" fill="none" stroke="currentColor" stroke-width="2"/></svg>';
        button.innerHTML = icon + '<span>Осень</span><span class="autumn-toggle-light" aria-hidden="true"></span>';
        button.setAttribute("aria-label", "Осенний режим");
        document.body.appendChild(button);

        let enabled = false;
        let leaves;

        function syncLeaves() {
            if (!enabled || reducedMotion.matches) {
                leaves?.remove();
                leaves = undefined;
                return;
            }
            if (leaves) return;
            leaves = document.createElement("div");
            leaves.className = "swagger-autumn-leaves";
            leaves.setAttribute("aria-hidden", "true");
            const colors = ["#c87531", "#d89e36", "#ac5436", "#a49345", "#dcad58"];
            for (let i = 0; i < 18; i++) {
                const leaf = document.createElement("span");
                leaf.className = "swagger-autumn-leaf";
                leaf.style.setProperty("--leaf-x", `${(i + Math.random()) * 100 / 18}vw`);
                leaf.style.setProperty("--leaf-size", `${22 + Math.random() * 20}px`);
                leaf.style.setProperty("--leaf-drift", `${Math.random() * 180 - 90}px`);
                leaf.style.setProperty("--leaf-duration", `${18 + Math.random() * 14}s`);
                leaf.style.setProperty("--leaf-delay", `${-Math.random() * 32}s`);
                leaf.style.setProperty("--leaf-sway", `${3 + Math.random() * 4}s`);
                leaf.style.color = colors[i % colors.length];
                leaf.innerHTML = '<svg viewBox="0 0 64 64" focusable="false">' + shapes[i % shapes.length] +
                    '<path d="M30 53 33 22M32 37 22 29M33 32 42 23" fill="none" stroke="#fff6de" stroke-opacity=".55" stroke-width="1.5"/></svg>';
                leaves.appendChild(leaf);
            }
            document.body.appendChild(leaves);
            syncVisibility();
        }

        function syncVisibility() { leaves?.classList.toggle("is-paused", document.hidden); }

        function setEnabled(value, persist = true) {
            enabled = value;
            document.body.classList.toggle("swagger-autumn", enabled);
            button.setAttribute("aria-pressed", String(enabled));
            button.title = enabled ? "Выключить осенний режим" : "Включить осенний режим";
            syncLeaves();
            if (persist) {
                try { localStorage.setItem(storageKey, String(enabled)); } catch (_) { /* Storage may be disabled. */ }
            }
        }

        button.addEventListener("click", () => setEnabled(!enabled));
        document.addEventListener("visibilitychange", syncVisibility);
        reducedMotion.addEventListener("change", syncLeaves);
        window.addEventListener("storage", event => {
            if (event.key === storageKey || event.key === null) setEnabled(event.newValue === "true", false);
        });
        let saved = false;
        try { saved = localStorage.getItem(storageKey) === "true"; } catch (_) { /* The toggle still works. */ }
        setEnabled(saved, false);
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount, {once: true});
    else mount();
})();
