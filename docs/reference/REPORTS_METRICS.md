# Reports metrics

## Dollars per mile (`$/mi`)

Per vehicle:

```
$/mi = (sum of fuel cost + sum of expenses for vehicleId)
     / (max(odometer) − min(odometer))
```

- Only fuel rows with **odometer > 0** contribute to min/max.
- **Partials** at the start or end of the odometer range are **acceptable noise**.
- Partial fills in the **middle** of the range do not require special filtering; max−min still reflects overall odo span when readings progress.
- The denominator is **not** limited to “full” fills only (full fills are used for MPG legs separately).

If there are fewer than two positive odometers, or max ≤ min, the UI shows **n/a**.

## Volume display

Fuel volumes in the database are stored in the user’s **preferred** unit (gallons or liters). Reports and fuel lists show that stored number with the preferred unit **label**; they do not re-convert.
