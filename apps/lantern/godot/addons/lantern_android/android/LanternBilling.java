package com.godot.game;

// LANTERN 2.0 by Yompix Games: покупки Google Play (Play Billing Library 7). Плагин Godot v2:
// manifest meta-data org.godotengine.plugin.v2.LanternBilling → Engine.get_singleton("LanternBilling").
// Работает только в сборке из Google Play (или у тестировщиков Play Console). В APK со стороны (sideload)
// подключение к Play не удаётся — приложение это видит (событие {"k":"state","ok":false}) и платное просто прячет.
// Проверка покупки — на сервере LANTERN (relay/billing.js, Google Play Developer API), здесь только показ окна оплаты.
//
// Методы для GDScript (все результаты — событиями, забирать через takeEvents(): JSON-массив):
//   start()                                  — подключиться к Play; событие {"k":"state","ok":bool,"code":int}
//   query(subsCsv, inappCsv)                 — цены; {"k":"products","list":[{id,type,price,title}]}
//   buy(productId)                           — окно оплаты; итог — {"k":"purchase",…} или {"k":"buy","code":…}
//   restore()                                — уже купленное; {"k":"purchase",…} для каждого
//   acknowledge(token) / consume(token)      — после проверки сервером; {"k":"ack"|"consumed","token","code"}

import android.app.Activity;
import android.util.Log;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.UsedByGodot;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LanternBilling extends GodotPlugin implements PurchasesUpdatedListener {
	private static final String TAG = "LANTERN-BILLING";
	private BillingClient client;
	private final Object lock = new Object();
	private JSONArray events = new JSONArray();
	private final Map<String, ProductDetails> details = new HashMap<>();

	public LanternBilling(Godot godot) {
		super(godot);
	}

	@Override
	public String getPluginName() {
		return "LanternBilling";
	}

	private void ev(JSONObject o) {
		synchronized (lock) { events.put(o); }
	}

	private static JSONObject obj(Object... kv) {
		JSONObject o = new JSONObject();
		try {
			for (int i = 0; i + 1 < kv.length; i += 2) o.put(String.valueOf(kv[i]), kv[i + 1]);
		} catch (Exception ignored) {}
		return o;
	}

	@UsedByGodot
	public String takeEvents() {
		synchronized (lock) {
			String s = events.toString();
			events = new JSONArray();
			return s;
		}
	}

	@UsedByGodot
	public void start() {
		Activity a = getActivity();
		if (a == null) { ev(obj("k", "state", "ok", false, "code", -100)); return; }
		if (client == null) {
			client = BillingClient.newBuilder(a).setListener(this)
				.enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build();
		}
		if (client.isReady()) { ev(obj("k", "state", "ok", true, "code", 0)); return; }
		client.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(BillingResult r) {
				ev(obj("k", "state", "ok", r.getResponseCode() == BillingClient.BillingResponseCode.OK, "code", r.getResponseCode()));
			}

			@Override
			public void onBillingServiceDisconnected() {
				ev(obj("k", "state", "ok", false, "code", -1));
			}
		});
	}

	@UsedByGodot
	public void query(String subsCsv, String inappCsv) {
		if (client == null || !client.isReady()) return;
		q(subsCsv, BillingClient.ProductType.SUBS);
		q(inappCsv, BillingClient.ProductType.INAPP);
	}

	private void q(String csv, String type) {
		List<QueryProductDetailsParams.Product> list = new ArrayList<>();
		for (String id : csv.split(",")) {
			if (!id.trim().isEmpty()) list.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id.trim()).setProductType(type).build());
		}
		if (list.isEmpty()) return;
		client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build(), (r, pds) -> {
			JSONArray arr = new JSONArray();
			if (pds != null) {
				for (ProductDetails d : pds) {
					details.put(d.getProductId(), d);
					String price = "";
					if (BillingClient.ProductType.SUBS.equals(d.getProductType())) {
						List<ProductDetails.SubscriptionOfferDetails> offers = d.getSubscriptionOfferDetails();
						if (offers != null && !offers.isEmpty()) {
							List<ProductDetails.PricingPhase> ph = offers.get(0).getPricingPhases().getPricingPhaseList();
							if (!ph.isEmpty()) price = ph.get(ph.size() - 1).getFormattedPrice();
						}
					} else if (d.getOneTimePurchaseOfferDetails() != null) {
						price = d.getOneTimePurchaseOfferDetails().getFormattedPrice();
					}
					arr.put(obj("id", d.getProductId(), "type", d.getProductType(), "price", price, "title", d.getName()));
				}
			}
			ev(obj("k", "products", "list", arr, "code", r.getResponseCode()));
		});
	}

	@UsedByGodot
	public void buy(String productId) {
		Activity a = getActivity();
		ProductDetails d = details.get(productId);
		if (client == null || !client.isReady() || a == null || d == null) { ev(obj("k", "buy", "id", productId, "code", -2)); return; }
		BillingFlowParams.ProductDetailsParams.Builder pb = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d);
		if (BillingClient.ProductType.SUBS.equals(d.getProductType())) {
			List<ProductDetails.SubscriptionOfferDetails> offers = d.getSubscriptionOfferDetails();
			if (offers == null || offers.isEmpty()) { ev(obj("k", "buy", "id", productId, "code", -3)); return; }
			pb.setOfferToken(offers.get(0).getOfferToken());
		}
		List<BillingFlowParams.ProductDetailsParams> l = new ArrayList<>();
		l.add(pb.build());
		final BillingFlowParams p = BillingFlowParams.newBuilder().setProductDetailsParamsList(l).build();
		a.runOnUiThread(() -> {
			BillingResult r = client.launchBillingFlow(a, p);
			if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) ev(obj("k", "buy", "id", productId, "code", r.getResponseCode()));
		});
	}

	@UsedByGodot
	public void restore() {
		if (client == null || !client.isReady()) return;
		for (String type : new String[]{BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP}) {
			client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build(), (r, list) -> {
				if (list != null) for (Purchase p : list) emit(p);
			});
		}
	}

	@UsedByGodot
	public void acknowledge(String token) {
		if (client == null || !client.isReady()) return;
		client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
			r -> ev(obj("k", "ack", "token", token, "code", r.getResponseCode())));
	}

	@UsedByGodot
	public void consume(String token) {
		if (client == null || !client.isReady()) return;
		client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build(),
			(r, t) -> ev(obj("k", "consumed", "token", token, "code", r.getResponseCode())));
	}

	private void emit(Purchase p) {
		String id = p.getProducts().isEmpty() ? "" : p.getProducts().get(0);
		ev(obj("k", "purchase", "id", id, "token", p.getPurchaseToken(), "state", p.getPurchaseState(),
			"acknowledged", p.isAcknowledged(), "order", String.valueOf(p.getOrderId())));
	}

	@Override
	public void onPurchasesUpdated(BillingResult r, List<Purchase> list) {
		if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
			for (Purchase p : list) emit(p);
		} else {
			ev(obj("k", "buy", "code", r.getResponseCode()));
		}
	}
}
