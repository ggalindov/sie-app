package sie.siejuridicos.documento;

import com.lowagie.text.pdf.PdfDate;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfPKCS7;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSignature;
import com.lowagie.text.pdf.PdfSignatureAppearance;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfString;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Aplica una firma digital PAdES/PKCS#7 EMBEBIDA en la estructura del PDF final de cada
// documento sellado (diccionario /Sig, ByteRange), usando una llave RSA + certificado X.509
// institucional propios (ver FirmaPadesClave). A diferencia de ApplicationSigningKeyService
// (Ed25519, firma solo el hash en base de datos, verificable únicamente a través de esta
// aplicación), esta firma la reconoce nativamente cualquier lector de PDF estándar (Adobe
// Acrobat Reader, Foxit, navegadores): "Documento firmado, el contenido no ha sido
// modificado desde la firma". El certificado es autofirmado (no proviene de una entidad de
// certificación acreditada ONAC) -- el lector mostrará la firma como criptográficamente
// válida, pero el certificado en sí como "no confiable" hasta que alguien lo agregue a su
// lista de confianza. Migrar a un certificado acreditado más adelante es cuestión de
// reemplazar la llave activa, sin tocar el resto del motor.
@Service
public class PadesSigningService {

    private static final Logger log = LoggerFactory.getLogger(PadesSigningService.class);
    private static final String ALGORITMO_CLAVE = "RSA";
    private static final int TAMANO_CLAVE_BITS = 3072;
    // Reserva para el CMS: la firma base pesa ~2 KB; el token RFC 3161 con cadena de la TSA suma varios KB.
    private static final int TAMANO_RESERVADO_FIRMA = 24576;
    private static final int VIGENCIA_CERTIFICADO_ANIOS = 10;

    private final FirmaPadesClaveRepository repository;
    private final AesGcmCipher aesGcmCipher;
    private final DocumentoClaveMaestraService claveMaestraService;
    private final Rfc3161TimestampClient timestampClient;
    // Mismo motivo/patrón que ApplicationSigningKeyService.claveActivaLock: cierra la
    // carrera de arranque en frío.
    private final Object claveActivaLock = new Object();

    // Sin cliente TSA: solo produce firmas PAdES-B-B.
    public PadesSigningService(FirmaPadesClaveRepository repository, AesGcmCipher aesGcmCipher,
                                DocumentoClaveMaestraService claveMaestraService) {
        this(repository, aesGcmCipher, claveMaestraService, null);
    }

    @Autowired
    public PadesSigningService(FirmaPadesClaveRepository repository, AesGcmCipher aesGcmCipher,
                                DocumentoClaveMaestraService claveMaestraService, Rfc3161TimestampClient timestampClient) {
        this.repository = repository;
        this.aesGcmCipher = aesGcmCipher;
        this.claveMaestraService = claveMaestraService;
        this.timestampClient = timestampClient;
    }

    // OID id-aa-signatureTimeStampToken (RFC 3161 / ETSI EN 319 142-1, nivel PAdES-B-T).
    static final ASN1ObjectIdentifier ID_AA_SIGNATURE_TIME_STAMP_TOKEN =
            new ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.14");

    public record ResultadoFirmaPades(byte[] pdfFirmado, String kid) {
    }

    // Firma el PDF completo (no solo su hash): el resultado tiene la firma PKCS#7 incrustada
    // en su propia estructura. Nunca lanza por un fallo de la firma digital en sí -- si algo
    // falla (certificado corrupto, PDF inválido), se registra y se devuelve el PDF SIN
    // firmar, para que un problema de esta capa adicional nunca bloquee el sellado del
    // documento (la firma de aplicación Ed25519 y el HMAC de CodigoVerificacionCalculator
    // siguen aplicando igual).
    @Transactional
    public Optional<ResultadoFirmaPades> firmarPdf(byte[] pdfSinFirmar, String razon, String ubicacion) {
        FirmaPadesClave clave = obtenerOCrearClaveActiva();
        byte[] privadaBytes = aesGcmCipher.descifrar(clave.getClavePrivadaCifrada(), clave.getClavePrivadaIv(),
                claveMaestraService.obtenerClaveMaestra(clave.getKekId()));
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITMO_CLAVE);
            PrivateKey privada = factory.generatePrivate(new PKCS8EncodedKeySpec(privadaBytes));
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            Certificate certificado = certFactory.generateCertificate(new ByteArrayInputStream(clave.getCertificado()));

            byte[] pdfFirmado = firmarConPkcs7Detached(pdfSinFirmar, privada, certificado, razon, ubicacion);
            return Optional.of(new ResultadoFirmaPades(pdfFirmado, clave.getKid()));
        } catch (Exception ex) {
            log.warn("No se pudo aplicar la firma digital PAdES al documento: {}", ex.getMessage(), ex);
            return Optional.empty();
        } finally {
            Arrays.fill(privadaBytes, (byte) 0);
        }
    }

    // Firma "externa" clásica de OpenPDF/iText2 (preClose/getRangeStream/close), la ÚNICA
    // forma de elegir explícitamente el SubFilter moderno /adbe.pkcs7.detached (CMS
    // SignedData con SHA-256): el atajo PdfSignatureAppearance.setCrypto(...,
    // PdfName.ADOBE_PPKLITE) usa SIEMPRE, internamente y sin poder cambiarlo, el SubFilter
    // legado /adbe.x509.rsa_sha1 (formato propietario de Adobe de los 90s con SHA-1, no
    // reconocido como CMS/PKCS7 válido por validadores modernos).
    private byte[] firmarConPkcs7Detached(byte[] pdfSinFirmar, PrivateKey privada, Certificate certificado,
                                           String razon, String ubicacion) throws Exception {
        PdfReader reader = new PdfReader(pdfSinFirmar);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfStamper stamper = PdfStamper.createSignature(reader, salida, '\0');
        PdfSignatureAppearance apariencia = stamper.getSignatureAppearance();
        apariencia.setReason(razon);
        apariencia.setLocation(ubicacion);
        apariencia.setCertificationLevel(PdfSignatureAppearance.NOT_CERTIFIED);

        Calendar fechaFirma = apariencia.getSignDateNullSafe();
        PdfSignature diccionarioFirma = new PdfSignature(PdfName.ADOBE_PPKLITE, PdfName.ADBE_PKCS7_DETACHED);
        diccionarioFirma.setReason(razon);
        diccionarioFirma.setLocation(ubicacion);
        diccionarioFirma.setDate(new PdfDate(fechaFirma));
        apariencia.setCryptoDictionary(diccionarioFirma);

        int tamanoEstimadoFirma = TAMANO_RESERVADO_FIRMA;
        Map<PdfName, Integer> exclusiones = new HashMap<>();
        exclusiones.put(PdfName.CONTENTS, tamanoEstimadoFirma * 2 + 2);
        apariencia.preClose(exclusiones);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream rango = apariencia.getRangeStream()) {
            byte[] buffer = new byte[8192];
            int leidos;
            while ((leidos = rango.read(buffer)) > 0) {
                digest.update(buffer, 0, leidos);
            }
        }
        byte[] hashContenido = digest.digest();

        // Patrón clásico de "firma externa" de iText2/OpenPDF: PdfPKCS7 arma la estructura
        // ASN.1 de atributos autenticados (que incluye el hash del contenido), esos
        // atributos -y NO el hash del contenido directamente- son lo que efectivamente se
        // firma con RSA.
        PdfPKCS7 pkcs7 = new PdfPKCS7(privada, new Certificate[]{certificado}, null, "SHA256", null, false);
        byte[] atributosAutenticados = pkcs7.getAuthenticatedAttributeBytes(hashContenido, fechaFirma, null);
        java.security.Signature firmadorRsa = java.security.Signature.getInstance("SHA256withRSA");
        firmadorRsa.initSign(privada);
        firmadorRsa.update(atributosAutenticados);
        byte[] firmaRsa = firmadorRsa.sign();
        pkcs7.setExternalDigest(firmaRsa, null, "RSA");
        byte[] firmaPkcs7 = pkcs7.getEncodedPKCS7(hashContenido, fechaFirma);
        firmaPkcs7 = incrustarSelloDeTiempo(firmaPkcs7, tamanoEstimadoFirma);
        if (firmaPkcs7.length > tamanoEstimadoFirma) {
            throw new IllegalStateException("El tamaño reservado para la firma PKCS7 (" + tamanoEstimadoFirma
                    + " bytes) fue insuficiente: se necesitaban " + firmaPkcs7.length + " bytes.");
        }

        byte[] contenidoRelleno = new byte[tamanoEstimadoFirma];
        System.arraycopy(firmaPkcs7, 0, contenidoRelleno, 0, firmaPkcs7.length);
        PdfDictionary actualizacion = new PdfDictionary();
        actualizacion.put(PdfName.CONTENTS, new PdfString(contenidoRelleno).setHexWriting(true));
        apariencia.close(actualizacion);

        return salida.toByteArray();
    }

    // PAdES-B-T: sella con RFC 3161 el VALOR de la firma (no el documento) y lo agrega como
    // atributo no firmado id-aa-signatureTimeStampToken del SignerInfo. Degradación
    // elegante: si no hay cliente TSA, la TSA no responde, el token no cabe en el espacio
    // reservado o algo falla, se devuelve el CMS original (PAdES-B-B) y el documento se
    // sella igual.
    private byte[] incrustarSelloDeTiempo(byte[] cmsBase, int tamanoReservado) {
        if (timestampClient == null) {
            return cmsBase;
        }
        try {
            CMSSignedData firmado = new CMSSignedData(cmsBase);
            SignerInformation firmante = firmado.getSignerInfos().getSigners().iterator().next();
            byte[] hashFirma = MessageDigest.getInstance("SHA-256").digest(firmante.getSignature());
            Optional<Rfc3161TimestampClient.ResultadoSello> sello = timestampClient.sellar(hashFirma);
            if (sello.isEmpty()) {
                log.warn("Sin TSA disponible: la firma PAdES queda en nivel B-B (sin sello de tiempo embebido).");
                return cmsBase;
            }
            ASN1Primitive token;
            try (ASN1InputStream in = new ASN1InputStream(sello.get().token())) {
                token = in.readObject();
            }
            ASN1EncodableVector atributos = new ASN1EncodableVector();
            AttributeTable existentes = firmante.getUnsignedAttributes();
            if (existentes != null) {
                atributos.addAll(existentes.toASN1EncodableVector());
            }
            atributos.add(new Attribute(ID_AA_SIGNATURE_TIME_STAMP_TOKEN, new DERSet(token)));
            SignerInformation conSello = SignerInformation.replaceUnsignedAttributes(
                    firmante, new AttributeTable(atributos));
            byte[] resultado = CMSSignedData.replaceSigners(firmado, new SignerInformationStore(conSello)).getEncoded();
            if (resultado.length > tamanoReservado) {
                log.warn("El sello de tiempo no cabe en el espacio reservado ({} > {} bytes): firma en nivel B-B.",
                        resultado.length, tamanoReservado);
                return cmsBase;
            }
            return resultado;
        } catch (Exception ex) {
            log.warn("No se pudo incrustar el sello de tiempo en la firma PAdES (queda B-B): {}", ex.getMessage());
            return cmsBase;
        }
    }

    public FirmaPadesClave obtenerPorKid(String kid) {
        return repository.findByKid(kid)
                .orElseThrow(() -> new IllegalStateException("No se encontró la llave PAdES '" + kid + "'."));
    }

    private FirmaPadesClave obtenerOCrearClaveActiva() {
        Optional<FirmaPadesClave> existente = repository.findByActivaTrue();
        if (existente.isPresent()) {
            return existente.get();
        }
        synchronized (claveActivaLock) {
            return repository.findByActivaTrue().orElseGet(this::generarYGuardarNueva);
        }
    }

    private FirmaPadesClave generarYGuardarNueva() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance(ALGORITMO_CLAVE);
            generador.initialize(TAMANO_CLAVE_BITS);
            KeyPair par = generador.generateKeyPair();

            X509Certificate certificado = autofirmarCertificado(par);
            byte[] certificadoDer = certificado.getEncoded();
            byte[] privada = par.getPrivate().getEncoded();

            String kekId = claveMaestraService.kekIdActual();
            AesGcmCipher.Resultado privadaCifrada = aesGcmCipher.cifrar(privada, claveMaestraService.obtenerClaveMaestra(kekId));
            Arrays.fill(privada, (byte) 0);

            LocalDateTime vigenteHasta = LocalDateTime.now().plusYears(VIGENCIA_CERTIFICADO_ANIOS);
            FirmaPadesClave clave = new FirmaPadesClave(UUID.randomUUID().toString(), certificadoDer,
                    privadaCifrada.datosCifrados(), privadaCifrada.iv(), kekId, vigenteHasta);
            return repository.save(clave);
        } catch (GeneralSecurityException | org.bouncycastle.operator.OperatorCreationException ex) {
            throw new IllegalStateException("No se pudo generar una nueva llave/certificado PAdES (RSA).", ex);
        }
    }

    // Certificado X.509 autofirmado (emisor = sujeto): sin depender de una CA externa.
    private X509Certificate autofirmarCertificado(KeyPair par)
            throws org.bouncycastle.operator.OperatorCreationException, java.security.cert.CertificateException {
        X500Name sujetoYEmisor = new X500Name("CN=SIE Jurídicos, O=SIE Jurídicos S.A.S., C=CO");
        BigInteger numeroSerie = new BigInteger(64, new SecureRandom());
        Date inicio = new Date();
        Date fin = Date.from(LocalDateTime.now().plusYears(VIGENCIA_CERTIFICADO_ANIOS)
                .atZone(ZoneId.systemDefault()).toInstant());

        X509v3CertificateBuilder constructor = new JcaX509v3CertificateBuilder(
                sujetoYEmisor, numeroSerie, inicio, fin, sujetoYEmisor, par.getPublic());
        ContentSigner firmador = new JcaContentSignerBuilder("SHA256withRSA").build(par.getPrivate());
        X509CertificateHolder holder = constructor.build(firmador);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}
