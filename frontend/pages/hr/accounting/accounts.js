// State Management
let rawAccountList = [];
let accountTree = [];
let collapsedNodes = new Set(); // Stores collapsed account IDs
let currentTypeFilter = 'ALL';
let currentSearchTerm = '';

// DOM Elements
const tbody = document.getElementById('coa_tree_tbody');
const searchInput = document.getElementById('coa_search_input');
const modal = document.getElementById('account_modal');
const form = document.getElementById('account_form');
const parentSelect = document.getElementById('form_parent_id');

// KPI elements
const kpiTotal = document.getElementById('kpi_total_count');
const kpiPosting = document.getElementById('kpi_posting_count');
const kpiSystem = document.getElementById('kpi_system_count');
const kpiInactive = document.getElementById('kpi_inactive_count');

// Initialize page
document.addEventListener('DOMContentLoaded', () => {
    loadAccounts();
    
    // Wire search input
    searchInput.addEventListener('input', (e) => {
        currentSearchTerm = e.target.value.trim().toLowerCase();
        renderTreeTable();
    });

    // Wire form submit
    form.addEventListener('submit', handleFormSubmit);
});

/**
 * Load Account data from API
 */
async function loadAccounts() {
    showLoading();
    try {
        const [treeRes, listRes] = await Promise.all([
            window.ddukApi.get('/api/v1/accounting/accounts/tree'),
            window.ddukApi.get('/api/v1/accounting/accounts/list')
        ]);
        
        accountTree = treeRes.data || [];
        rawAccountList = listRes.data || [];
        
        updateKPIs();
        populateParentSelect();
        renderTreeTable();
    } catch (error) {
        showToast(error.message || '데이터 로딩 실패', 'danger');
        tbody.innerHTML = `
            <tr>
                <td colspan="10" style="text-align: center; padding: 40px; color: var(--danger);">
                    <div style="display: flex; flex-direction: column; align-items: center; gap: 8px;">
                        <i data-lucide="alert-circle" style="width: 24px; height: 24px;"></i>
                        <span>계정과목 데이터를 가져오는데 실패했습니다: ${error.message}</span>
                        <button onclick="window.location.reload()" style="margin-top: 12px; padding: 6px 12px; border-radius: 6px; border: 1px solid var(--border); cursor: pointer;">다시 시도</button>
                    </div>
                </td>
            </tr>
        `;
        if (window.lucide) lucide.createIcons();
    }
}

/**
 * Update KPI values
 */
function updateKPIs() {
    if (!kpiTotal) return;
    
    const total = rawAccountList.length;
    // isLeaf == true인 계정이 실제로 기표(posting) 가능한 계정
    const posting = rawAccountList.filter(a => a.isLeaf && a.allowPosting).length;
    const system = rawAccountList.filter(a => a.systemAccount).length;
    const inactive = rawAccountList.filter(a => a.status === 'INACTIVE').length;

    kpiTotal.textContent = total;
    kpiPosting.textContent = posting;
    kpiSystem.textContent = system;
    kpiInactive.textContent = inactive;
}

/**
 * Render Tree Table Rows
 */
function renderTreeTable() {
    tbody.innerHTML = '';
    
    if (accountTree.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="10" style="text-align: center; padding: 40px; color: var(--text-muted);">
                    등록된 계정과목이 없습니다. "표준 계정 초기화" 버튼을 클릭하여 시드 데이터를 적재하십시오.
                </td>
            </tr>
        `;
        return;
    }

    // Traverse tree and build table rows
    const rows = [];
    
    function traverse(nodes, visible = true) {
        for (const node of nodes) {
            // Apply filtering logic
            const matchesType = currentTypeFilter === 'ALL' || node.type === currentTypeFilter;
            const matchesSearch = !currentSearchTerm || 
                node.code.toLowerCase().includes(currentSearchTerm) || 
                node.name.toLowerCase().includes(currentSearchTerm) ||
                (node.englishName && node.englishName.toLowerCase().includes(currentSearchTerm));

            let showRow = visible;
            
            // If filtering or searching is active, normal collapse state might be overridden
            if (currentTypeFilter !== 'ALL' || currentSearchTerm) {
                showRow = matchesType && matchesSearch;
            }

            rows.push({
                node,
                showRow,
                isIndentActive: currentTypeFilter === 'ALL' && !currentSearchTerm
            });

            // Children traversal
            if (node.children && node.children.length > 0) {
                const isCollapsed = collapsedNodes.has(node.id);
                // Child nodes are visible only if parent is visible AND parent is not collapsed
                traverse(node.children, visible && !isCollapsed);
            }
        }
    }

    traverse(accountTree);

    // If searching/filtering has no matches
    const visibleCount = rows.filter(r => r.showRow).length;
    if (visibleCount === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="10" style="text-align: center; padding: 30px; color: var(--text-muted);">
                    검색 조건에 맞는 계정과목이 없습니다.
                </td>
            </tr>
        `;
        return;
    }

    rows.forEach(({ node, showRow, isIndentActive }) => {
        if (!showRow) return;

        const tr = document.createElement('tr');
        tr.className = 'coa-row-visible';

        const indentClass = isIndentActive ? `tree-indent-${node.level - 1}` : 'tree-indent-0';
        const hasChildren = node.children && node.children.length > 0;
        const isCollapsed = collapsedNodes.has(node.id);

        // Name cell with toggle button
        let toggleHtml = '';
        if (isIndentActive && hasChildren) {
            const rotClass = isCollapsed ? 'collapsed' : '';
            toggleHtml = `
                <button class="tree-toggle-btn ${rotClass}" data-id="${node.id}">
                    <i data-lucide="chevron-down"></i>
                </button>
            `;
        } else if (isIndentActive) {
            toggleHtml = `<span style="display: inline-block; width: 20px;"></span>`;
        }

        const iconName = hasChildren ? 'folder' : 'file-text';
        const iconColor = hasChildren ? '#818cf8' : '#94a3b8';

        // Columns mappings
        tr.innerHTML = `
            <td style="font-family: monospace; font-weight: 600; color: #334155;">${node.code}</td>
            <td class="${indentClass}">
                <div class="tree-node-cell">
                    ${toggleHtml}
                    <i data-lucide="${iconName}" style="width: 16px; height: 16px; color: ${iconColor}; flex-shrink: 0;"></i>
                    <span style="font-weight: ${hasChildren ? '600' : 'normal'}">${node.name}</span>
                </div>
            </td>
            <td style="color: var(--text-secondary);">${node.englishName || '-'}</td>
            <td><span class="badge-type ${node.type}">${node.type}</span></td>
            <td><span class="badge-side ${node.normalBalance}">${node.normalBalance === 'DEBIT' ? '차변' : '대변'}</span></td>
            <td style="text-align: center; color: var(--text-muted);">${node.sortOrder}</td>
            <td>
                <span class="badge-posting ${node.allowPosting ? 'allowed' : 'blocked'}">
                    ${node.allowPosting ? '기표가능' : '기표불가'}
                </span>
            </td>
            <td>
                ${node.systemAccount ? `
                    <span class="badge-system">
                        <i data-lucide="shield" style="width: 10px; height: 10px;"></i>
                        시스템
                    </span>
                ` : '<span style="color: var(--text-muted); font-size:0.75rem;">일반</span>'}
            </td>
            <td><span class="badge-status ${node.status}">${node.status === 'ACTIVE' ? '활성' : '비활성'}</span></td>
            <td style="text-align: center;">
                <div class="actions-cell">
                    <button class="btn-icon-action btn-edit" data-id="${node.id}" title="계정 수정">
                        <i data-lucide="edit-3" style="width: 14px; height: 14px;"></i>
                    </button>
                    <button class="btn-icon-action delete btn-delete" data-id="${node.id}" ${node.systemAccount ? 'disabled' : ''} title="${node.systemAccount ? '시스템 보호 계정은 삭제할 수 없습니다' : '계정 삭제'}">
                        <i data-lucide="trash-2" style="width: 14px; height: 14px;"></i>
                    </button>
                </div>
            </td>
        `;

        tbody.appendChild(tr);
    });

    // Reinitialize icons inside table
    if (window.lucide) lucide.createIcons();

    // Bind events
    document.querySelectorAll('.tree-toggle-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const id = parseInt(e.currentTarget.getAttribute('data-id'), 10);
            if (collapsedNodes.has(id)) {
                collapsedNodes.delete(id);
            } else {
                collapsedNodes.add(id);
            }
            renderTreeTable();
        });
    });

    document.querySelectorAll('.btn-edit').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const id = parseInt(e.currentTarget.getAttribute('data-id'), 10);
            openEditModal(id);
        });
    });

    document.querySelectorAll('.btn-delete').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const id = parseInt(e.currentTarget.getAttribute('data-id'), 10);
            handleDelete(id);
        });
    });
}

/**
 * Build Parent Account Dropdown List with Indentation Option Text
 */
function populateParentSelect(excludeId = null) {
    // Collect child IDs to exclude if updating, to prevent selecting lower hierarchy nodes
    const excludedIds = new Set();
    if (excludeId) {
        excludedIds.add(excludeId);
        function collectChildren(nodes) {
            for (const n of nodes) {
                excludedIds.add(n.id);
                if (n.children && n.children.length > 0) {
                    collectChildren(n.children);
                }
            }
        }
        const currentAccount = rawAccountList.find(a => a.id === excludeId);
        // Find in tree to get its children
        function findInTree(nodes) {
            for (const n of nodes) {
                if (n.id === excludeId) {
                    if (n.children) collectChildren(n.children);
                    return;
                }
                if (n.children) findInTree(n.children);
            }
        }
        findInTree(accountTree);
    }

    parentSelect.innerHTML = `<option value="">(최상위 루트 계정으로 등록)</option>`;
    
    function traverse(nodes, depth = 0) {
        for (const node of nodes) {
            if (excludedIds.has(node.id)) continue; // 순환참조 방지를 위한 배제
            
            // 시스템 보호 계정은 기표가능(allowPosting) 여부와 상관없이 부모가 될 수 있으며,
            // 보통 말단(Leaf) 노드만 기표 가능하게 설계하므로 children을 가질 부모 계정은 allowPosting=false여야 한다.
            // 하지만 DDUK ERP의 유연한 확장을 위해, 모든 계정을 부모로 선택할 수 있도록 하되
            // UI 단에서 들여쓰기를 지원한다.
            const prefix = '&nbsp;&nbsp;'.repeat(depth) + '└ ';
            const option = document.createElement('option');
            option.value = node.id;
            option.innerHTML = `${prefix}${node.code} ${node.name}`;
            parentSelect.appendChild(option);

            if (node.children && node.children.length > 0) {
                traverse(node.children, depth + 1);
            }
        }
    }

    traverse(accountTree);
}

/**
 * Filter Tree view by AccountType
 */
window.filterByType = function(type) {
    currentTypeFilter = type;
    
    // Update active tab styling
    document.querySelectorAll('.segmented_tab').forEach(tab => {
        const isTarget = tab.textContent.includes(type === 'ALL' ? '전체' : getKoreanTypeName(type));
        tab.classList.toggle('active', isTarget);
    });

    renderTreeTable();
};

function getKoreanTypeName(type) {
    switch (type) {
        case 'ASSET': return '자산';
        case 'LIABILITY': return '부채';
        case 'EQUITY': return '자본';
        case 'REVENUE': return '수익';
        case 'EXPENSE': return '비용';
        default: return type;
    }
}

/**
 * Handle Auto-Balance assign on AccountType change
 */
window.handleTypeChange = function() {
    const type = document.getElementById('form_type').value;
    const balanceSelect = document.getElementById('form_balance');

    // Asset and Expense default to DEBIT (차변), others default to CREDIT (대변)
    if (type === 'ASSET' || type === 'EXPENSE') {
        balanceSelect.value = 'DEBIT';
    } else {
        balanceSelect.value = 'CREDIT';
    }
};

/**
 * Show loading indicator inside table
 */
function showLoading() {
    tbody.innerHTML = `
        <tr>
            <td colspan="10" style="text-align: center; padding: 40px; color: var(--text-muted);">
                <div style="display: flex; flex-direction: column; align-items: center; gap: 8px;">
                    <i data-lucide="loader-2" class="animate-spin" style="width: 24px; height: 24px; color: var(--accent);"></i>
                    <span>서버에서 계정과목 최신 정보를 가져오는 중입니다...</span>
                </div>
            </td>
        </tr>
    `;
    if (window.lucide) lucide.createIcons();
}

/**
 * Modal control: Open for creation
 */
window.openCreateModal = function() {
    form.reset();
    document.getElementById('form_account_id').value = '';
    document.getElementById('modal_title').textContent = '계정과목 등록';
    document.getElementById('btn_modal_submit').textContent = '등록';

    // Enable inputs
    document.getElementById('form_code').disabled = false;
    document.getElementById('form_type').disabled = false;
    document.getElementById('form_balance').disabled = false;
    document.getElementById('form_parent_id').disabled = false;
    document.getElementById('form_allow_posting').disabled = false;
    document.getElementById('form_status').disabled = false;

    // Hide system account warning banner
    document.getElementById('system_account_alert').classList.add('coa-row-hidden');

    populateParentSelect();
    modal.classList.add('is_active');
    handleTypeChange(); // Set default debit/credit direction
};

/**
 * Modal control: Open for modification
 */
function openEditModal(id) {
    const account = rawAccountList.find(a => a.id === id);
    if (!account) return;

    document.getElementById('form_account_id').value = account.id;
    document.getElementById('modal_title').textContent = '계정과목 수정';
    document.getElementById('btn_modal_submit').textContent = '저장';

    document.getElementById('form_code').value = account.code;
    document.getElementById('form_type').value = account.type;
    document.getElementById('form_name').value = account.name;
    document.getElementById('form_english_name').value = account.englishName || '';
    document.getElementById('form_balance').value = account.normalBalance;
    document.getElementById('form_sort_order').value = account.sortOrder;
    document.getElementById('form_description').value = account.description || '';
    document.getElementById('form_allow_posting').checked = account.allowPosting;
    document.getElementById('form_status').value = account.status;

    // Load parent dropdown without recursion
    populateParentSelect(account.id);
    document.getElementById('form_parent_id').value = account.parentId || '';

    // Safety checks for System Account
    if (account.systemAccount) {
        document.getElementById('system_account_alert').classList.remove('coa-row-hidden');
        // Disable core configurations
        document.getElementById('form_code').disabled = true;
        document.getElementById('form_type').disabled = true;
        document.getElementById('form_balance').disabled = true;
        document.getElementById('form_parent_id').disabled = true;
        document.getElementById('form_allow_posting').disabled = true;
        document.getElementById('form_status').disabled = true;
    } else {
        document.getElementById('system_account_alert').classList.add('coa-row-hidden');
        // Enable
        document.getElementById('form_code').disabled = true; // Code stays locked for master key integrity
        document.getElementById('form_type').disabled = true; // Type also stays locked to keep transaction history safe
        document.getElementById('form_balance').disabled = false;
        document.getElementById('form_parent_id').disabled = false;
        document.getElementById('form_allow_posting').disabled = false;
        document.getElementById('form_status').disabled = false;
    }

    modal.classList.add('is_active');
}

window.closeAccountModal = function() {
    modal.classList.remove('is_active');
};

/**
 * Handle form submission (Create or Update)
 */
async function handleFormSubmit(e) {
    e.preventDefault();

    const id = document.getElementById('form_account_id').value;
    const isUpdate = !!id;

    // Compile payload
    const payload = {
        name: document.getElementById('form_name').value.trim(),
        englishName: document.getElementById('form_english_name').value.trim() || null,
        sortOrder: parseInt(document.getElementById('form_sort_order').value, 10) || 0,
        description: document.getElementById('form_description').value.trim() || null
    };

    if (!isUpdate) {
        payload.code = document.getElementById('form_code').value.trim();
        payload.type = document.getElementById('form_type').value;
        payload.normalBalance = document.getElementById('form_balance').value;
        payload.allowPosting = document.getElementById('form_allow_posting').checked;
        payload.status = document.getElementById('form_status').value;
        payload.parentId = document.getElementById('form_parent_id').value ? parseInt(document.getElementById('form_parent_id').value, 10) : null;
    } else {
        const account = rawAccountList.find(a => a.id === parseInt(id, 10));
        if (!account.systemAccount) {
            payload.allowPosting = document.getElementById('form_allow_posting').checked;
            payload.status = document.getElementById('form_status').value;
            payload.parentId = document.getElementById('form_parent_id').value ? parseInt(document.getElementById('form_parent_id').value, 10) : null;
        }
    }

    try {
        if (isUpdate) {
            await window.ddukApi.put(`/api/v1/accounting/accounts/${id}`, payload);
            showToast('계정과목이 성공적으로 수정되었습니다.', 'success');
        } else {
            await window.ddukApi.post('/api/v1/accounting/accounts', payload);
            showToast('새 계정과목이 등록되었습니다.', 'success');
        }
        
        closeAccountModal();
        loadAccounts();
    } catch (error) {
        showToast(error.message || '저장 실패', 'danger');
    }
}

/**
 * Delete account (Soft Delete)
 */
async function handleDelete(id) {
    const account = rawAccountList.find(a => a.id === id);
    if (!account) return;

    if (confirm(`진짜로 계정과목 [${account.code}] ${account.name}을 삭제하시겠습니까?\n하위 노드가 있거나 기표된 내역이 있을 경우 삭제되지 않을 수 있습니다.`)) {
        try {
            await window.ddukApi.delete(`/api/v1/accounting/accounts/${id}`);
            showToast('계정과목이 정상적으로 삭제되었습니다.', 'success');
            loadAccounts();
        } catch (error) {
            showToast(error.message || '삭제 실패', 'danger');
        }
    }
}

/**
 * Manual seed trigger
 */
window.triggerCoaSeeding = async function() {
    if (confirm('모든 기본 표준 계정과목 시드 데이터를 서버에 새로 적재/복원하시겠습니까?\n(기존 수동 추가한 계정은 유지되며, 삭제된 기본 계정은 복원됩니다)')) {
        showLoading();
        try {
            await window.ddukApi.post('/api/v1/accounting/accounts/seed');
            showToast('표준 계정과목 150개가 성공적으로 적재/복원되었습니다.', 'success');
            loadAccounts();
        } catch (error) {
            showToast(error.message || '시드 적재 실패', 'danger');
            loadAccounts();
        }
    }
};

/**
 * Notification helper (Toast)
 */
function showToast(message, type = 'success') {
    const container = document.getElementById('toast_container');
    if (!container) return;

    const toast = document.createElement('div');
    
    // erp_components toast class mapping
    let toastType = type;
    if (type === 'danger') toastType = 'error';
    toast.className = `toast show ${toastType}`;
    toast.style.position = 'static';
    toast.style.display = 'flex';
    toast.style.alignItems = 'center';
    toast.style.gap = '8px';
    
    let iconName = 'check-circle';
    if (type === 'danger') iconName = 'x-circle';
    if (type === 'warning') iconName = 'alert-triangle';

    toast.innerHTML = `
        <i data-lucide="${iconName}" style="width: 18px; height: 18px; flex-shrink: 0;"></i>
        <span>${message}</span>
    `;

    container.appendChild(toast);
    if (window.lucide) lucide.createIcons();

    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}
