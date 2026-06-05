/**
 * Base Dashboard Widget Class
 */
export class DashboardWidget {
    constructor(id, container, config = {}) {
        this.id = id;
        this.container = container;
        this.config = {
            title: '',
            permission: [],
            ...config
        };
        this.state = {
            data: null,
            loading: false,
            error: null,
            lastRefreshed: null
        };
    }

    /**
     * Initial Setup
     */
    async init() {
        this.renderFrame();
        await this.load();
    }

    /**
     * Fetch Data (to be implemented by subclasses)
     */
    async load() {
        this.setLoading(true);
        try {
            const data = await this.fetchData();
            this.state.data = data;
            this.state.lastRefreshed = new Date();
            this.state.error = null;
        } catch (err) {
            console.error(`Widget [${this.id}] load error:`, err);
            this.state.error = '데이터를 불러오지 못했습니다.';
        } finally {
            this.setLoading(false);
            this.render();
        }
    }

    /**
     * Abstract method to fetch specific widget data
     */
    async fetchData() {
        return null;
    }

    /**
     * Reload Widget Data
     */
    async reload() {
        await this.load();
    }

    /**
     * Render common frame (title, reload btn, etc)
     */
    renderFrame() {
        this.container.innerHTML = `
            <div class="erp_widget" id="widget_${this.id}" style="height: 100%; display: flex; flex-direction: column; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;">
                <div class="erp_widget_header" style="padding: 12px 16px; border-bottom: 1px solid #f1f5f9; display: flex; justify-content: space-between; align-items: center;">
                    <h3 style="font-size: 0.875rem; font-weight: 600; color: #475569; margin: 0;">${this.config.title}</h3>
                    <div class="widget_actions">
                        <button type="button" class="btn_reload" style="background: none; border: none; cursor: pointer; color: #94a3b8; font-size: 0.75rem;">새로고침</button>
                    </div>
                </div>
                <div class="erp_widget_body" style="flex: 1; position: relative;">
                    <div class="widget_content"></div>
                    <div class="widget_overlay" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: none; align-items: center; justify-content: center; background: rgba(255,255,255,0.7);"></div>
                </div>
            </div>
        `;
        this.container.querySelector('.btn_reload').addEventListener('click', () => this.reload());
    }

    /**
     * Update Loading State
     */
    setLoading(isLoading) {
        this.state.loading = isLoading;
        const overlay = this.container.querySelector('.widget_overlay');
        if (overlay) {
            overlay.style.display = isLoading ? 'flex' : 'none';
            overlay.innerHTML = '<div class="erp_spinner" style="width: 20px; height: 20px; border: 2px solid #e2e8f0; border-top-color: #3182ce; border-radius: 50%; animation: spin 1s linear infinite;"></div>';
        }
    }

    /**
     * Main render logic (to be implemented by subclasses)
     */
    render() {
        const content = this.container.querySelector('.widget_content');
        if (this.state.error) {
            content.innerHTML = `<div style="padding: 20px; text-align: center; color: #e53e3e; font-size: 0.875rem;">${this.state.error}</div>`;
            return;
        }
        this.renderContent(content);
    }

    /**
     * Render specific content (to be implemented by subclasses)
     */
    renderContent(container) {
        // Implementation in subclasses
    }

    /**
     * Cleanup
     */
    destroy() {
        this.container.innerHTML = '';
    }
}
