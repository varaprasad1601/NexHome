import AppLink from '../components/common/AppLink';
import Icon from '../components/common/Icon';
import PageHeader from '../components/common/PageHeader';

/** About Us page. */
export default function AboutPage() {
  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="About Us"
        eyebrow="GET TO KNOW US"
        description="A simpler and more organized way to connect homes and skilled professionals."
      >
        About <span className="text-[#168F5B]">NexHome</span>.
      </PageHeader>

      <div className="container max-w-6xl py-12">

        {/* About Text Card matching requested copy */}
        <div className="bg-white rounded-3xl p-8 sm:p-12 border border-gray-100 shadow-sm mb-12 space-y-5">
          <p className="text-sm sm:text-base text-gray-700 leading-relaxed font-normal">
            Welcome to NexHome, a household services platform designed to deliver customers with skilled service professionals for everyday home maintenance and household needs.
          </p>
          <p className="text-sm sm:text-base text-gray-700 leading-relaxed font-normal">
            From routine household tasks to specialized services, NexHome aims to make finding the right assistance easier, more convenient, and more organized.
          </p>
          <p className="text-sm sm:text-base text-gray-700 leading-relaxed font-normal">
            We believe that maintaining a comfortable home should not be complicated. By bringing customers and service professionals together, we work toward creating a simpler and more reliable household service experience.
          </p>
        </div>

        {/* Bottom Callout Banner matching uploaded reference */}
        <div className="bg-white rounded-3xl p-10 sm:p-14 border border-gray-100 shadow-sm text-center">
          <h2 className="text-3xl sm:text-4xl font-black text-[#0B315F] tracking-tight mb-3 leading-tight">
            Household help begins with <span className="text-[#168F5B]">one conversation</span>.
          </h2>
          <p className="text-sm text-gray-500 max-w-xl mx-auto mb-8">
            Let us know what you need in Nuzvid and Nearby Areas. We can review your service enquiry during business hours.
          </p>
          <AppLink
            className="bg-[#0B438B] hover:bg-[#08336a] text-white px-8 py-3.5 rounded-full text-sm font-bold inline-flex items-center gap-2 shadow-md transition"
            to="/contact"
          >
            Contact NexHome <Icon name="arrow-right" size={16} />
          </AppLink>
        </div>
      </div>
    </div>
  );
}
