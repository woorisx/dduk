        document.addEventListener('DOMContentLoaded', () => {
            const session = window.ddukSession?.requireRole?.(['ADMIN', 'INVENTORY', 'HR'], { redirectToLogin: true });
            if (!session) return;
            if (window.lucide) lucide.createIcons();
            const params = new URLSearchParams(location.search);
            const vendorId = params.get('vendorId');
            const vendorName = params.get('vendorName') || '';
            function resolveCurrentMemberId(){const loginId=(localStorage.getItem('loginId')||'').toLowerCase();const role=(localStorage.getItem('role')||'').toUpperCase();if(loginId==='inventory'||role==='INVENTORY')return'2';if(loginId==='hr'||role==='HR')return'3';if(loginId==='admin'||role==='ADMIN')return'1';return localStorage.getItem('memberId')||localStorage.getItem('userId')||'1'}
            const registeredById = params.get('registeredById') || resolveCurrentMemberId();
            const apiOrigin = window.ddukSession?.getApiBaseUrl?.() || window.ddukApi?.getBaseUrl?.() || '';
            const form = document.getElementById('item-form'), msg = document.getElementById('message'), saveBtn = document.getElementById('btn-save');
            document.getElementById('vendor-name').value = vendorName || (vendorId ? `거래처 ID ${vendorId}` : '미지정');
            document.getElementById('registered-by-id').value = registeredById;
            document.getElementById('name').value = params.get('name') || '';
            function headers(){const t=localStorage.getItem('token');const h={'Content-Type':'application/json'};if(t)h.Authorization=`Bearer ${t}`;return h}
            async function readError(r){const t=await r.text();if(!t)return`HTTP ${r.status}`;try{const j=JSON.parse(t);return j.message||j.error||t}catch{return t}}
            function setMsg(t,type='info'){msg.className=`mt-5 min-h-6 text-sm font-semibold ${type==='error'?'text-red-600':type==='ok'?'text-emerald-600':'text-gray-500'}`;msg.textContent=t}
            form.addEventListener('submit', async e => {
                e.preventDefault();
                if(!form.checkValidity()){form.reportValidity();return}
                const data = Object.fromEntries(new FormData(form).entries());
                const payload = {name:data.name,itemType:data.itemType,category:data.category,spec:data.spec,barcode:data.barcode,unit:data.unit,standardCost:Number(data.standardCost||0),unitPrice:Number(data.unitPrice||0),safetyStock:Number(data.safetyStock||0),active:data.active==='true',vendorId:vendorId?Number(vendorId):null,registeredById:Number(registeredById)};
                const original = saveBtn.innerHTML; saveBtn.disabled = true; saveBtn.innerHTML = '등록 중...'; setMsg('품목을 등록하는 중입니다.');
                try {
                    const r = await fetch(`${apiOrigin}/api/v1/inventory/items`, {method:'POST',headers:headers(),body:JSON.stringify(payload)});
                    if(!r.ok) throw new Error(await readError(r));
                    const item = await r.json();
                    if (window.opener && !window.opener.closed) {
                        if (window.opener.ddukPurchaseRequest?.addCreatedItem) window.opener.ddukPurchaseRequest.addCreatedItem(item);
                        else window.opener.postMessage({type:'DDUK_ITEM_CREATED', item}, location.origin);
                    }
                    setMsg('품목이 등록되었습니다.','ok');
                    window.close();
                } catch (error) {
                    setMsg(`품목 등록 실패: ${error.message}`,'error');
                    saveBtn.disabled = false; saveBtn.innerHTML = original;
                }
            });
        });
    
