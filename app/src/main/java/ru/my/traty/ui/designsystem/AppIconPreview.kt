package ru.my.traty.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.my.traty.R
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
fun AppIconLayered(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Traty App Icon Showcase", showBackground = true, backgroundColor = 0xFF090D16)
@Composable
fun TratyAppIconShowcasePreview() {
    TratyTheme(darkTheme = true) {
        Surface(
            color = Color(0xFF090D16),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = "Иконка приложения «Траты»",
                    color = Color.White,
                    fontSize = 20.sp,
                )

                // Row of different launcher shapes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Circle mask (Pixel / Pure Android)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0x3010B981), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIconLayered(modifier = Modifier.size(108.dp))
                        }
                        Text(
                            text = "Круг (Pixel)",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                        )
                    }

                    // Squircle / Rounded Square (OneUI / Xiaomi)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, Color(0x3010B981), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIconLayered(modifier = Modifier.size(108.dp))
                        }
                        Text(
                            text = "Сквиркл (One UI)",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                        )
                    }

                    // Themed Icon (Material You / Android 13+)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF064E3B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                                contentDescription = null,
                                modifier = Modifier.size(108.dp),
                            )
                        }
                        Text(
                            text = "Themed (M3)",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Full 108dp unclipped layer preview
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(108.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0x40334155), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIconLayered(modifier = Modifier.size(108.dp))
                    }
                    Text(
                        text = "Полный холст Adaptive Icon (108x108 dp)",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
