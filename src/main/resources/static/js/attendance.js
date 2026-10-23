(() => {
    const input = document.getElementById('attendancePhoto');
    const preview = document.getElementById('attendancePhotoPreview');
    let previewUrl;
    if (input && preview) {
        input.addEventListener('change', () => {
            if (previewUrl) URL.revokeObjectURL(previewUrl);
            const file = input.files[0];
            const valid = file && ['image/jpeg', 'image/png'].includes(file.type) && file.size <= 5 * 1024 * 1024;
            input.setCustomValidity(file && !valid ? 'Choose a JPEG or PNG photo up to 5 MB.' : '');
            preview.hidden = !valid;
            document.getElementById('attendancePhotoPlaceholder').hidden = !!valid;
            if (valid) {
                previewUrl = URL.createObjectURL(file);
                preview.src = previewUrl;
            } else { preview.removeAttribute('src'); input.reportValidity(); }
        });
        window.addEventListener('pagehide', () => { if (previewUrl) URL.revokeObjectURL(previewUrl); });
    }
    const type = document.getElementById('personType');
    const person = document.getElementById('personId');
    if (type && person) {
        const filter = () => {
            person.value = '';
            for (const option of person.options) {
                const excluded = option.dataset.personType && option.dataset.personType !== type.value;
                option.disabled = !!excluded;
                option.hidden = !!excluded;
            }
        };
        type.addEventListener('change', filter);
        filter();
    }
})();
