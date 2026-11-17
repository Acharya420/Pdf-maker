package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.remote.onGoogleSignInClicked
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGradientButton
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateIn = true
        delay(1100)
        onFinished()
    }

    val scale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0.72f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "splashScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        label = "splashAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepNavy, MidnightNavy, Color(0xFF1A103D))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
                .padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_icon_pdf_maker),
                contentDescription = "PDF Maker emblem",
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(30.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "PDF Maker",
                style = MaterialTheme.typography.displayLarge,
                color = PureWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Liquid Glass Document Studio",
                style = MaterialTheme.typography.bodyLarge,
                color = LuminousLavender
            )
        }
    }
}

private data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
fun OnboardingScreen(
    onContinueAsGuest: () -> Unit,
    onSignedIn: () -> Unit
) {
    val slides = remember {
        listOf(
            OnboardingSlide(
                title = "Image to PDF Studio",
                subtitle = "Select photos from Gallery or Files, drag to reorder, crop, rotate, tune DPI & target file size.",
                icon = Icons.Filled.PictureAsPdf
            ),
            OnboardingSlide(
                title = "Complete Offline PDF Toolkit",
                subtitle = "Merge, split, compress, reorganize pages, rotate, convert to images, and password-protect PDFs privately.",
                icon = Icons.Filled.AutoAwesome
            ),
            OnboardingSlide(
                title = "Private Vault & Optional Sync",
                subtitle = "Your photos and PDFs never leave your phone. Continue as Guest or sign in with Google to sync settings.",
                icon = Icons.Filled.Lock
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LiquidGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onContinueAsGuest,
                    modifier = Modifier.testTag("onboarding_skip_btn")
                ) {
                    Text("Skip")
                }
            }

            Image(
                painter = painterResource(id = R.drawable.img_onboarding_studio),
                contentDescription = "PDF Maker studio preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.height(24.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val slide = slides[page]
                LiquidGlassCard(
                    elevated = true,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(22.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(RoyalPurple.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = RoyalPurple
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = slide.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 14.dp)
            ) {
                repeat(slides.size) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (active) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) RoyalPurple else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }

            if (authError != null) {
                Text(
                    text = authError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (pagerState.currentPage < slides.lastIndex) {
                LiquidGradientButton(
                    text = "Next",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_next_btn")
                )
            } else {
                LiquidGradientButton(
                    text = "Continue as Guest (Offline)",
                    onClick = onContinueAsGuest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_guest_btn")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        isSigningIn = true
                        authError = null
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isSigningIn = false
                                onSignedIn()
                            },
                            onAuthError = { msg ->
                                isSigningIn = false
                                authError = msg
                            },
                            scope = scope,
                            onAuthCancelled = { isSigningIn = false }
                        )
                    },
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_google_signin_btn")
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Sign in with Google for Cloud Config")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
