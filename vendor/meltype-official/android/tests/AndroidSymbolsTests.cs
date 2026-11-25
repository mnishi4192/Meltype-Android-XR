// SPDX-License-Identifier: GPL-3.0-or-later

using Meltype.Config;

namespace Meltype.Tests;

// Run in the upstream Meltype.Core.Tests project with the Android Core sources.
internal static class AndroidSymbolsTests
{
    [Test]
    public static void JapaneseModeUsesConfiguredSymbolsAfterLatin()
    {
        var detector = CompositionTests.Detector;
        var preferPunctuation = detector.UseJapanesePunctuationAfterEnglish;
        var preferJapaneseContext = detector.PreferJapaneseSymbolsInEnglishContext;
        detector.PreferJapaneseSymbolsInEnglishContext = true;
        try
        {
            foreach (var (key, expected) in new[] { (".", "。"), (",", "、"), ("/", "・") })
            {
                var composing = new CompositionTests.Keyboard(live: true) { Slash = SlashStyle.MiddleDot };
                detector.UseJapanesePunctuationAfterEnglish = () => true;
                composing.Host.PrecedingText = "Hello ";
                composing.Type("world" + key);
                Assert.Equal("world" + expected, composing.Showing);
                composing.Type("\n");
                Assert.Equal("world" + expected, composing.Host.Document);

                var committed = new CompositionTests.Keyboard(live: true) { Slash = SlashStyle.MiddleDot };
                committed.Type("GitHub\n");
                committed.Type(key);
                Assert.Equal(expected, committed.Showing);
                committed.Type("\n");
                Assert.Equal("GitHub" + expected, committed.Host.Document);

                detector.UseJapanesePunctuationAfterEnglish = () => false;
                var disabled = new CompositionTests.Keyboard { Slash = SlashStyle.Slash };
                disabled.Host.PrecedingText = "Hello ";
                disabled.Type("world" + key + "\n");
                Assert.Equal("world" + key, disabled.Host.Document);

                detector.UseJapanesePunctuationAfterEnglish = () => true;
                var direct = new CompositionTests.Keyboard(direct: true) { Slash = SlashStyle.MiddleDot };
                direct.Type("ABC" + key);
                Assert.Equal("ABC" + key, direct.Host.Document);
            }

            var fullWidth = new CompositionTests.Keyboard { Punctuation = PunctuationStyle.FullWidthCommaPeriod, Slash = SlashStyle.FullWidth };
            fullWidth.Type("ABC.\nABC,\nABC/\n");
            Assert.Equal("ABC．ABC，ABC／", fullWidth.Host.Document);

            foreach (var token in new[] { "https://example.com/path", "foo@example.com", "./docs/readme", "tetr.io", "1.5", "1,000", "3/4", "ABC/XYZ" })
            {
                var composing = new CompositionTests.Keyboard(live: true) { Slash = SlashStyle.MiddleDot };
                composing.Type(token + "\n");
                Assert.Equal(token, composing.Host.Document);
            }
        }
        finally
        {
            detector.UseJapanesePunctuationAfterEnglish = preferPunctuation;
            detector.PreferJapaneseSymbolsInEnglishContext = preferJapaneseContext;
        }
    }
}
