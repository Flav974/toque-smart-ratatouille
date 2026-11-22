public class T {
  public static void main(String[] a) throws Exception {
    int nulls = 0, i;
    for (i = 0; i < 200000; i++) { if (app.toque.core.i18n.I18nKt.tr("Cuisson manuelle") == null) { nulls++; if (nulls == 1) System.out.println("premier null a " + i); } }
    System.out.println("appels=" + i + " nulls=" + nulls);
  }
}
