/**
 * ERP Common Dashboard System
 */

/**
 * Base Widget Class
 */
export class BaseWidget {
    constructor(id, container, config = {}) {
        this.id = id;
        this.container = container;
        this.config = { title: '', ...config };
        this.state = { data: null, loading: false, error: null };
    }

    async init() {
        this.renderFrame();
        await this.load();
    }

    async load() {
        this.setLoading(true);
        try {
            this.state.data = await this.fetchData();
        } catch (err) {
            this.state.error = '데이터 로드 실패';
        } finally {
            this.setLoading(false);
            this.render();
        }
    }

    async fetchData() { return null; }
    
    async reload() { await this.load(); }

    renderFrame() {
        this.container.innerHTML = `
            <div class="erp_widget" id="widget_${this.id}">
                <div class="erp_widget_header">
                    <h3>${this.config.title}</h3>
                    <button type="button" class="btn_reload">↻</button>
                </div>
                <div class="erp_widget_body">
                    <div class="widget_content"></div>
                    <div class="widget_overlay"></div>
                </div>
            </div>
        `;
        this.container.querySelector('.btn_reload').addEventListener('click', () => this.reload());
    }

    setLoading(isLoading) {
        this.state.loading = isLoading;
        const overlay = this.container.querySelector('.widget_overlay');
        if (overlay) overlay.style.display = isLoading ? 'flex' : 'none';
    }

    render() {
        const content = this.container.querySelector('.widget_content');
        if (this.state.error) {
            content.innerHTML = `<div class="widget_error">${this.state.error}</div>`;
            return;
        }
        this.renderContent(content);
    }

    renderContent(container) {}
    
    destroy() { this.container.innerHTML = ''; }
}

/**
 * Dashboard Registry
 */
export class DashboardRegistry {
    constructor() {
        this.widgetMap = new Map();
        this.instances = [];
    }

    register(type, widgetClass) {
        this.widgetMap.set(type, widgetClass);
    }

    createWidgets(layout, containers, userRole) {
        this.instances.forEach(i => i.destroy());
        this.instances = layout.map(item => {
            const WidgetClass = this.widgetMap.get(item.type);
            const container = containers[item.id] || document.getElementById(item.id);
            if (WidgetClass && container) {
                const instance = new WidgetClass(item.id, container, item.config);
                instance.init();
                return instance;
            }
            return null;
        }).filter(Boolean);
        return this.instances;
    }
}
