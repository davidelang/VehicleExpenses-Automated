# Vehicle Expenses Automated — Navigation Map

## Menu → Pages
- **Quick Fill-up**: Main entry point for recording fuel fill-ups via camera/OCR.
- **Manage Vehicles**: Add, edit, or remove vehicles. Set up OCR reference photos here.
- **New Expense Entry**: Record non-fuel expenses (repairs, insurance, etc.).
- **Expense List**: View historical non-fuel expenses.
- **Import Old Pictures**: Batch process existing odometer photos for OCR.
- **Reports & Charts**: View fuel economy, cost trends, and summaries.
- **Settings**: Configure sync, units, and storage.
- **Help**: User manual and troubleshooting.
- **About**: Version information and credits.
- **Alignment Experiment**: Debugging tool for testing OCR alignment logic.

## Page Flows
- **Quick Fill-up** → **Reports** (after successful save)
- **Manage Vehicles** → (pop back to previous screen)
- **New Expense Entry** → **Reports** (after successful save)
- **Expense List** → (pop back to previous screen)
- **Import Old Pictures** → (pop back to previous screen)
- **Reports & Charts** → **Quick Fill-up** (via menu or back button)
- **Settings** → (pop back to previous screen)
- **Help** → (pop back to previous screen)
- **About** → (pop back to previous screen)
- **Alignment Experiment** → (pop back to previous screen)

## Notes
- **Start destination**: Quick Fill-up (`quickfill`)
- **Navigation Drawer**: Accessible from all top-level screens.
- **Deep Linking**: Not currently implemented.
