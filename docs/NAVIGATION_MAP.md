# Vehicle Expenses Automated — Navigation Map

## Menu → Pages
menu -> Quick Fill-up
menu -> Add New Vehicle
menu -> Expense Entry
menu -> Expense List
menu -> Import Old Pictures
menu -> Reports & Charts
menu -> Settings
menu -> Help
menu -> About

## Page Flows
Quick Fill-up -> Reports (after successful save)
Add New Vehicle -> previous screen (popBackStack on Save or Cancel)
Expense Entry -> Reports (after successful save)
Expense List -> previous screen
Import Old Pictures -> previous screen
Reports & Charts -> Quick Fill-up (main return point)
Settings -> previous screen
Help -> previous screen
About -> previous screen

## Notes
- Start destination: Quick Fill-up
- All "Save" actions return to Reports or previous screen
- Gallery-only flows (import old pictures) are accessible from menu and advanced buttons
