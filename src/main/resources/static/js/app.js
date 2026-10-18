document.addEventListener("input", function (event) {
    if (!event.target.matches("[data-table-search]")) {
        return;
    }

    const query = event.target.value.trim().toLowerCase();
    const table = document.querySelector(event.target.dataset.tableSearch);

    if (!table) {
        return;
    }

    table.querySelectorAll("tbody tr").forEach(function (row) {
        row.hidden = !row.textContent.toLowerCase().includes(query);
    });
});

function syncRolePosition(roleSelect) {
    const form = roleSelect.closest("form") || document;
    const positionField = form.querySelector("[data-position-field]");

    if (!positionField) {
        return;
    }

    const positionSelect = positionField.querySelector("select");
    const isEmployee = roleSelect.value === "EMPLOYEE";

    positionField.hidden = !isEmployee;

    if (positionSelect) {
        positionSelect.required = isEmployee;

        if (!isEmployee) {
            positionSelect.value = "";
        }
    }
}

document.addEventListener("change", function (event) {
    if (event.target.matches("[data-role-select]")) {
        syncRolePosition(event.target);
    }
});

document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("[data-role-select]").forEach(syncRolePosition);
});
