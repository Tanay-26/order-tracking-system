#!/usr/bin/env python3
"""
Rider GPS simulator for the Real-Time Order Tracking System.

What it does
------------
1. Creates a real order through the REST API.
2. Walks the order through the 6-stage state machine.
3. From PICKED_UP onwards it posts a GPS ping every 3 seconds, moving the rider
   along a straight line from the restaurant to the customer.
4. Marks the order DELIVERED when the rider arrives.

Usage
-----
    pip install requests
    python3 rider_simulator.py                       # one rider, default coordinates
    python3 rider_simulator.py --riders 5            # five riders in parallel
    python3 rider_simulator.py --interval 3 --steps 20
    python3 rider_simulator.py --order ORD-1A2B3C4D --rider RIDER-1   # reuse an order
"""

import argparse
import json
import random
import sys
import threading
import time

try:
    import requests
except ImportError:
    sys.exit("Missing dependency. Run:  pip install requests")

# Restaurant (Cyber Hub, Gurugram) -> Customer (DLF Phase 3)
START_LAT, START_LNG = 28.4949, 77.0885
END_LAT, END_LNG = 28.4986, 77.1025

STAGES = ["CONFIRMED", "PREPARING", "PICKED_UP", "OUT_FOR_DELIVERY"]


def log(tag, msg):
    print(f"[{time.strftime('%H:%M:%S')}] [{tag}] {msg}", flush=True)


class RiderSimulator:
    def __init__(self, base_url, rider_id, interval, steps, order_ref=None):
        self.base = base_url.rstrip("/")
        self.rider_id = rider_id
        self.interval = interval
        self.steps = steps
        self.order_ref = order_ref
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

    # ---------- REST helpers -------------------------------------------------
    def create_order(self):
        payload = {
            "customerId": f"CUST-{random.randint(1000, 9999)}",
            "customerName": "Test Customer",
            "restaurantName": "Biryani Blues, Cyber Hub",
            "pickupAddress": "Cyber Hub, DLF Cyber City, Gurugram",
            "deliveryAddress": "Tower B, DLF Phase 3, Gurugram",
            "totalAmount": round(random.uniform(250, 900), 2),
        }
        r = self.session.post(f"{self.base}/api/v1/orders", data=json.dumps(payload), timeout=5)
        r.raise_for_status()
        self.order_ref = r.json()["orderRef"]
        log(self.rider_id, f"Created order {self.order_ref}")
        return self.order_ref

    def assign_rider(self):
        payload = {"riderId": self.rider_id, "riderName": "Simulated Rider", "riderPhone": "+919999900000"}
        r = self.session.patch(f"{self.base}/api/v1/orders/{self.order_ref}/rider",
                               data=json.dumps(payload), timeout=5)
        r.raise_for_status()
        log(self.rider_id, f"Assigned to {self.order_ref}")

    def set_status(self, status, note=""):
        payload = {"status": status, "note": note}
        r = self.session.patch(f"{self.base}/api/v1/orders/{self.order_ref}/status",
                               data=json.dumps(payload), timeout=5)
        if r.status_code >= 400:
            log(self.rider_id, f"Status {status} rejected: {r.text}")
            return False
        log(self.rider_id, f"Status -> {status}")
        return True

    def send_location(self, lat, lng, speed, bearing):
        payload = {
            "orderRef": self.order_ref,
            "latitude": round(lat, 6),
            "longitude": round(lng, 6),
            "speedKmph": round(speed, 1),
            "bearing": round(bearing, 1),
        }
        started = time.perf_counter()
        r = self.session.post(f"{self.base}/api/v1/riders/{self.rider_id}/location",
                              data=json.dumps(payload), timeout=5)
        elapsed_ms = (time.perf_counter() - started) * 1000
        if r.status_code >= 400:
            log(self.rider_id, f"Ping rejected: {r.text}")
        else:
            log(self.rider_id, f"GPS {lat:.5f},{lng:.5f}  round-trip {elapsed_ms:6.1f} ms")

    # ---------- main loop ----------------------------------------------------
    def run(self):
        try:
            if not self.order_ref:
                self.create_order()
            self.assign_rider()

            # Walk through the early stages with a short pause between them.
            for stage in STAGES:
                time.sleep(self.interval)
                self.set_status(stage, f"Simulated transition to {stage}")

            # Now stream GPS pings while moving towards the customer.
            for step in range(1, self.steps + 1):
                fraction = step / self.steps
                lat = START_LAT + (END_LAT - START_LAT) * fraction
                lng = START_LNG + (END_LNG - START_LNG) * fraction
                # A little jitter so the path looks like a real road, not a ruler.
                lat += random.uniform(-0.00012, 0.00012)
                lng += random.uniform(-0.00012, 0.00012)
                speed = random.uniform(14, 32)
                bearing = random.uniform(40, 60)
                self.send_location(lat, lng, speed, bearing)
                time.sleep(self.interval)

            self.set_status("DELIVERED", "Handed over to the customer")
            log(self.rider_id, f"Finished order {self.order_ref}")

        except requests.exceptions.ConnectionError:
            log(self.rider_id, "Cannot reach the API. Is the Spring Boot app running on :8080?")
        except Exception as exc:
            log(self.rider_id, f"Simulation failed: {exc}")


def main():
    parser = argparse.ArgumentParser(description="Simulate riders pinging GPS every N seconds.")
    parser.add_argument("--url", default="http://localhost:8080", help="Base URL of the API")
    parser.add_argument("--riders", type=int, default=1, help="How many riders to simulate in parallel")
    parser.add_argument("--interval", type=float, default=3.0, help="Seconds between GPS pings")
    parser.add_argument("--steps", type=int, default=20, help="How many pings before delivery")
    parser.add_argument("--order", default=None, help="Reuse an existing orderRef (single rider only)")
    parser.add_argument("--rider", default=None, help="Explicit rider id (single rider only)")
    args = parser.parse_args()

    if args.riders == 1:
        rider_id = args.rider or f"RIDER-{random.randint(100, 999)}"
        RiderSimulator(args.url, rider_id, args.interval, args.steps, args.order).run()
        return

    threads = []
    for i in range(args.riders):
        rider_id = f"RIDER-{100 + i}"
        sim = RiderSimulator(args.url, rider_id, args.interval, args.steps)
        t = threading.Thread(target=sim.run, name=rider_id, daemon=False)
        threads.append(t)
        t.start()
        time.sleep(0.4)   # stagger the starts

    for t in threads:
        t.join()


if __name__ == "__main__":
    main()
