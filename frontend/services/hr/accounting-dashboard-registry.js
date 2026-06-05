/**
 * Accounting Dashboard Widget Registry
 */

export class DashboardRegistry {
    constructor() {
        this.widgets = new Map();
        this.activeWidgets = [];
    }

    /**
     * Register a widget definition
     */
    register(id, widgetClass, defaultConfig = {}) {
        this.widgets.set(id, {
            widgetClass,
            defaultConfig
        });
    }

    /**
     * Create widget instances based on layout and permissions
     */
    createWidgets(layout, containers, userRole) {
        this.activeWidgets.forEach(w => w.destroy());
        this.activeWidgets = [];

        layout.forEach(item => {
            const def = this.widgets.get(item.id);
            const container = containers[item.id] || document.getElementById(item.id);
            
            if (def && container) {
                // Permission check
                const config = { ...def.defaultConfig, ...item.config };
                if (config.permission && config.permission.length > 0 && !config.permission.includes(userRole)) {
                    return;
                }

                const instance = new def.widgetClass(item.id, container, config);
                this.activeWidgets.push(instance);
                instance.init();
            }
        });

        return this.activeWidgets;
    }

    /**
     * Reload all active widgets
     */
    reloadAll() {
        this.activeWidgets.forEach(w => w.reload());
    }
}

export const dashboardRegistry = new DashboardRegistry();
