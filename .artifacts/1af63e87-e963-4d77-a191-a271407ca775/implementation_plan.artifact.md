# Implementation Plan - Missing Screens and Design Refinement

Add missing screens (Splash, Role Selection, Onboarding) and refine existing auth screens (Sign In, Sign Up) to match the provided design image.

## Proposed Changes

### UI Components

#### [NEW] [fragment_splash.xml](file:///C:/Users/SUDIP/OneDrive/Documents/Askvocate/Askvocate/app/src/main/res/layout/fragment_splash.xml)
- White background with centered logo.

#### [NEW] [fragment_role_selection.xml](file:///C:/Users/SUDIP/OneDrive/Documents/Askvocate/Askvocate/app/src/main/res/layout/fragment_role_selection.xml)
- "How will you use Askvocate?" with "I need legal help" and "I'm a lawyer" options.
- Custom radio button style cards.

#### [NEW] [fragment_onboarding.xml](file:///C:/Users/SUDIP/OneDrive/Documents/Askvocate/Askvocate/app/src/main/res/layout/fragment_onboarding.xml)
- Pager-style layout for multi-step onboarding.
- Support for "Welcome to Askvocate!" and "Let's start exploring".

#### [MODIFY] [fragment_sign_in.xml](file:///C:/Users/SUDIP/OneDrive/Documents/Askvocate/Askvocate/app/src/main/res/layout/fragment_sign_in.xml)
- Refine to match the design with "Welcome Back" header and bottom card.

#### [MODIFY] [fragment_sign_up.xml](file:///C:/Users/SUDIP/OneDrive/Documents/Askvocate/Askvocate/app/src/main/res/layout/fragment_sign_up.xml)
- Refine to match the "already late." header style and card layout.

### Logic & Navigation

#### [NEW] [SplashFragment.kt](file:///C:/Users/SUDIP/OneDrive/Documents/Askvocate/Askvocate/app/src/main/java/com/example/askvocate/ui/auth/SplashFragment.kt)
- 2-second delay before navigating to Role Selection.

#### [NEW] [RoleSelectionFragment.kt](file:///C:/Users/SUDIP/OneDrive/Documents/Askvocate/Askvocate/app/src/main/java/com/example/askvocate/ui/auth/RoleSelectionFragment.kt)
- Handles role selection and navigation to Onboarding.

#### [NEW] [OnboardingFragment.kt](file:///C:/Users/SUDIP/OneDrive/Documents/Askvocate/Askvocate/app/src/main/java/com/example/askvocate/ui/auth/OnboardingFragment.kt)
- Manages onboarding steps and final transition to Sign In.

#### [MODIFY] [nav_graph.xml](file:///C:/Users/SUDIP/OneDrive/Documents/Askvocate/Askvocate/app/src/main/res/navigation/nav_graph.xml)
- Update start destination to Splash.
- Add new destinations and actions: Splash -> Role Selection -> Onboarding -> Sign In.

## Verification Plan

### Automated Tests
- N/A (Focus on UI consistency)

### Manual Verification
- Deploy to emulator/device.
- Verify the entire flow from Splash to Home.
- Compare each screen with the provided design image for visual accuracy.
