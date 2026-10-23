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

    positionField.hidden = roleSelect.value === "HR";

    if (positionSelect) {
        positionSelect.required = isEmployee;
        positionSelect.disabled = !isEmployee;

        if (!isEmployee) {
            positionSelect.value = "";
        }
    }
}

function syncDepartmentMentors(departmentSelect) {
    const form = departmentSelect.closest("form");
    const mentorSelect = form.querySelector("[data-mentor-select]");
    const optionsTemplate = form.querySelector("[data-mentor-options]");

    if (!mentorSelect || !optionsTemplate) {
        return;
    }

    const department = departmentSelect.value.trim().toLowerCase();
    const previousMentor = mentorSelect.value;
    const matches = Array.from(optionsTemplate.content.querySelectorAll("option")).filter(function (option) {
        return department && option.dataset.department.trim().toLowerCase() === department;
    });
    const placeholder = !department ? "Select Department First"
        : matches.length ? "Select Senior Mentor" : "No Senior Mentors Available";

    mentorSelect.replaceChildren(new Option(placeholder, ""));
    matches.forEach(function (option) {
        mentorSelect.appendChild(option.cloneNode(true));
    });
    mentorSelect.value = matches.some(function (option) {
        return option.value === previousMentor;
    }) ? previousMentor : "";
    mentorSelect.disabled = !department || matches.length === 0;
}

document.addEventListener("change", function (event) {
    if (event.target.matches("[data-role-select]")) {
        syncRolePosition(event.target);
    }
    if (event.target.matches("[data-intern-department]")) {
        syncDepartmentMentors(event.target);
    }
});

document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("[data-role-select]").forEach(syncRolePosition);
    document.querySelectorAll("[data-intern-department]").forEach(syncDepartmentMentors);
});
