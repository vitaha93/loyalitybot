// Admin Panel JavaScript

// HTMX Configuration
document.body.addEventListener('htmx:configRequest', function(event) {
    // Add CSRF token if needed
    // event.detail.headers['X-CSRF-TOKEN'] = document.querySelector('meta[name="_csrf"]')?.content;
});

// Handle HTMX errors
document.body.addEventListener('htmx:responseError', function(event) {
    console.error('HTMX Error:', event.detail);
    alert('Помилка при виконанні запиту. Спробуйте ще раз.');
});

// Customer search selection
function selectCustomer(element) {
    const id = element.dataset.id;
    const name = element.dataset.name;

    document.getElementById('selected-customer-id').value = id;
    document.getElementById('customer-search').value = name;
    document.getElementById('customer-results').innerHTML = '';
}

// Mobile sidebar toggle
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.querySelector('.sidebar-overlay');
    const body = document.body;

    sidebar.classList.toggle('open');
    overlay.classList.toggle('active');
    body.classList.toggle('sidebar-open');
}

// Confirm dangerous actions
document.querySelectorAll('[data-confirm]').forEach(function(element) {
    element.addEventListener('click', function(event) {
        if (!confirm(this.dataset.confirm)) {
            event.preventDefault();
        }
    });
});

// Auto-hide alerts after 5 seconds
document.querySelectorAll('.alert').forEach(function(alert) {
    setTimeout(function() {
        alert.style.transition = 'opacity 0.5s';
        alert.style.opacity = '0';
        setTimeout(function() {
            alert.remove();
        }, 500);
    }, 5000);
});

// Format numbers with thousands separator
function formatNumber(num) {
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
}

// Initialize any dynamic content
document.addEventListener('DOMContentLoaded', function() {
    // Add mobile menu button if needed
    if (window.innerWidth <= 768) {
        const header = document.querySelector('.page-header');
        if (header && !document.querySelector('.mobile-menu-btn')) {
            const btn = document.createElement('button');
            btn.className = 'btn mobile-menu-btn';
            btn.innerHTML = '&#9776;';
            btn.onclick = toggleSidebar;
            header.insertBefore(btn, header.firstChild);
        }
    }
});

// Close sidebar when clicking outside on mobile
document.addEventListener('click', function(event) {
    const sidebar = document.querySelector('.sidebar');
    const menuBtn = document.querySelector('.mobile-menu-btn');

    if (window.innerWidth <= 768 &&
        sidebar.classList.contains('open') &&
        !sidebar.contains(event.target) &&
        event.target !== menuBtn) {
        sidebar.classList.remove('open');
    }
});

// Table Sorting Functionality
function initTableSorting() {
    document.querySelectorAll('table.sortable').forEach(function(table) {
        const headers = table.querySelectorAll('th[data-sort]');
        headers.forEach(function(header, headerIndex) {
            header.style.cursor = 'pointer';
            header.classList.add('sortable-header');

            // Add sort indicator
            if (!header.querySelector('.sort-indicator')) {
                const indicator = document.createElement('span');
                indicator.className = 'sort-indicator';
                indicator.innerHTML = ' ⇅';
                header.appendChild(indicator);
            }

            header.addEventListener('click', function() {
                sortTable(table, headerIndex, header.dataset.sort, header);
            });
        });
    });
}

function sortTable(table, columnIndex, sortType, header) {
    const tbody = table.querySelector('tbody');
    const rows = Array.from(tbody.querySelectorAll('tr'));

    // Determine sort direction
    const currentDir = header.dataset.sortDir || 'none';
    const newDir = currentDir === 'asc' ? 'desc' : 'asc';

    // Reset all other headers
    table.querySelectorAll('th[data-sort]').forEach(function(th) {
        th.dataset.sortDir = 'none';
        th.classList.remove('sort-asc', 'sort-desc');
        const indicator = th.querySelector('.sort-indicator');
        if (indicator) indicator.innerHTML = ' ⇅';
    });

    // Set current header
    header.dataset.sortDir = newDir;
    header.classList.add('sort-' + newDir);
    const indicator = header.querySelector('.sort-indicator');
    if (indicator) indicator.innerHTML = newDir === 'asc' ? ' ↑' : ' ↓';

    // Sort rows
    rows.sort(function(a, b) {
        const cellA = a.cells[columnIndex];
        const cellB = b.cells[columnIndex];

        let valA = getCellValue(cellA, sortType);
        let valB = getCellValue(cellB, sortType);

        let comparison = 0;
        if (sortType === 'number' || sortType === 'currency') {
            comparison = valA - valB;
        } else if (sortType === 'date') {
            comparison = valA - valB;
        } else {
            comparison = valA.toString().localeCompare(valB.toString(), 'uk');
        }

        return newDir === 'asc' ? comparison : -comparison;
    });

    // Re-append rows in sorted order
    rows.forEach(function(row) {
        tbody.appendChild(row);
    });
}

function getCellValue(cell, sortType) {
    const text = cell.textContent.trim();

    if (sortType === 'number') {
        return parseFloat(text.replace(/[^\d.-]/g, '')) || 0;
    } else if (sortType === 'currency') {
        // Remove currency symbols and parse as number
        return parseFloat(text.replace(/[^\d.,-]/g, '').replace(',', '.')) || 0;
    } else if (sortType === 'date') {
        // Handle dd.MM.yyyy HH:mm format
        const parts = text.split(' ');
        const dateParts = parts[0].split('.');
        if (dateParts.length === 3) {
            const dateStr = dateParts[2] + '-' + dateParts[1] + '-' + dateParts[0];
            return new Date(dateStr + (parts[1] ? ' ' + parts[1] : '')).getTime() || 0;
        }
        // Handle yyyy-MM-dd format
        return new Date(text).getTime() || 0;
    }

    return text.toLowerCase();
}

// Initialize sorting on page load and after HTMX swaps
document.addEventListener('DOMContentLoaded', initTableSorting);
document.body.addEventListener('htmx:afterSwap', initTableSorting);
